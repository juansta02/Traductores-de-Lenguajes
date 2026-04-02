package procesador;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Stack;
import tslib.TS_Gestor.DescripcionAtributo;
import tslib.TS_Gestor.Tabla;
import tslib.TS_Gestor.TipoDatoAtributo;

// Analizador Sintáctico Descendente Recursivo
public class ASin {

    private static Token<?> tokenActual;
    private static BufferedWriter ptwParse;

    //declaraciones del analizador semantico 
    private static boolean tsGlobal;
    private static boolean zonaDeclaracion;
    private static Integer despGlobal, despLocal;
    private static Integer numEtiq;

    //declaraciones gestor errores
    private static Stack<String> TokensPosibles = new Stack<>();
    private static String funcionAnterior;
    private static boolean huboCoincidencia = true;

    //declaracion debug
    private static boolean debug = false;

    public static void setOutputParseFile(BufferedWriter parsePtr) {
        ptwParse = parsePtr;
    }


    /* Inicia el análisis sintáctico.
     * Es el punto de entrada del analizador sintáctico.
     * Se encarga de:
     *  - Iniciar la tabla de símbolos
     *  - Solicitar el primer token al analizador léxico
     *  - LLamar al símbolo incial de la gramatica (P)
     *  - Comprobar el final del fichero
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public static void analizar() {

        // Iniciar con primer token
        numEtiq = 0;
        iniciarTS();

        printParse("Descendente");
        // Empezar por el axioma inicial
        try {
            tokenActual = ALex.generarToken();
            P();  // Comienza el análisis sintáctico
            tokenActualCoincideCualquiera("EOF");

        } catch (ErrorSintacticoException e) {

        }

        // Verificar fin y errores
        if (tokenActual.getId().equals(ALex.tok_id.get("EOF"))) {
            System.out.println("Análisis completado correctamente");
        } else if (!tokenActual.getId().equals(ALex.tok_id.get("EOF"))) {
            GestorError.writeError("\b", "No se ha podido analizar el fichero entero");

        }

        GestorError.printError();

    }

    //Funcion Correspondiente al simbolo no terminal: P
    //Acciones posibles:
    //      P-> D R
    private static Atributos P() {

        printParse("1");    //Accion Sintactica: P-> D R

        //ASem:     TSG:= CrearTS ()
        Procesador.gestorTS.createTSGlobal();
        //ASem:     TSActual:= TSG
        tsGlobal = true;
        //ASem:     desp_global:=0
        despGlobal = 0;
        //ASem:     zona_decl:= true 
        zonaDeclaracion = true;

        //ASin: D
        D();

        //ASint: R
        Atributos atrR = R();
        // ASem:
        //      ¡If (R.program ≠ 1) Then Error (“Debe haber 1 y solo 1 Program”) 

        if (atrR.getProgramCount() > 1) {
            GestorError.writeError("semántico", "Solo debe haber un Programa Principal (PROGRAM)");
        } else if (atrR.getProgramCount() < 1) {
            GestorError.writeError("semántico", "Siempre tiene que haber un Programa Principal (PROGRAM)");
        }
        //DestruirTS(TSG)
        destroyTable(Tabla.GLOBAL);

        return null;
    }

    //Funcion Correspondiente al simbolo no terminal: R
    //Acciones posibles:
    //      R -> PP R
    //      R -> PR R
    //      R -> PF R
    private static Atributos R() {

        Atributos atrR = new Atributos();
        Atributos atrR1;

        if (tokenActualCoincideCualquiera("PROGRAM")) {

            printParse("2");    // 2. R -> PP R

            //  ASin: PP
            PP();
            // ASin: R
            atrR1 = R();

            // Asem: R.program:= 1 + R1.program
            atrR.setProgramCount(atrR1.getProgramCount() + 1);

        } else if (tokenActualCoincideCualquiera("PROCEDURE")) {
            printParse("3");    //3.  R -> PR R
            //  ASin: PR
            PR();
            // ASin: R
            atrR1 = R();

            // Asem: R.program:= R1.program
            atrR.setProgramCount(atrR1.getProgramCount());

        } else if (tokenActualCoincideCualquiera("FUNCTION")) {
            printParse("4");    //4.  R -> PF R

            // ASin: PF
            PF();
            // ASin: R
            atrR1 = R();

            // Asem: R.program:= R1.program
            atrR.setProgramCount(atrR1.getProgramCount());

        } else if (tokenActualCoincideCualquiera("EOF")) {
            printParse("5");    //5.  R -> λ

            // ASem: R.program:= 0
            atrR.setProgramCount(0);
        }

        debug(atrR);
        return atrR;
    }

    // Funcion Correspondiente al simbolo no terminal: PP
    // Acciones posibles:
    //      PP -> program id ; D Bloque ;
    private static Atributos PP() {

        if (tokenActualCoincideCualquiera("PROGRAM")) {

            Atributos atrID;
            Atributos atrBloque;
            printParse("6");    //      PP -> program id ; D Bloque ;

            //ASin: program
            match("PROGRAM");
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();

            //ASin: ;
            match("PYC");

            //ASem: InsetarTipoTS (id.pos, vacío→vacío)
            Procesador.gestorTS.setTipo(idPos, "procedimiento");    //dado que a la tabla no se puede añadir la palabra programa, se le llamara procedimiento y tendra etiqueta personalizada ("main")
            Procesador.gestorTS.setValorAtributoEnt(idPos, "numParametro", 0);
            Procesador.gestorTS.setValorAtributoCad(idPos, "tipoRetorno", "vacío");

            //ASem: InsertarEtiqTS (id.pos, “main”)
            Procesador.gestorTS.setValorAtributoCad(idPos, "etiqueta", "main");
            //ASem: TSL:= CrearTS ()
            Procesador.gestorTS.createTSLocal();
            //ASem: TSActual:= TSL
            tsGlobal = false;
            //ASem: despl_local:= 0
            despLocal = 0;

            //ASin: D
            D();

            //ASem: zona_decl = false
            zonaDeclaracion = false;

            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");

            //ASem:
            // if (Bloque.tipo = tipo_error)   
            //      then Error (“Error detectado en el desarrollo del cuerpo del Programa principal”)  
            // if(Bloque.tipoRet ≠ tipo_ok AND Bloque.tipoRet ≠ vacío)   
            //      then Error (“Programa Principal con instruccion de retorno no vacio”)  
            //if(Bloque.exit > 0)   
            //      then Error (“Exit fuera de bucle detectado en Programa Principal”)  
            if (atrBloque.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Error detectado en el desarrollo del cuerpo del Programa principal");
            }
            if (!atrBloque.getRet().equals("tipo_ok") && !atrBloque.getRet().equals("vacío")) {
                GestorError.writeError("semántico", "Programa Principal con instrucción de retorno no vacío");
            }
            if (atrBloque.getExit() > 0) {
                GestorError.writeError("semántico", "EXIT fuera de bucle detectado en Programa Principal");
            }

            // ASem: destruirTS (TSL)
            destroyTable(Tabla.LOCAL);
            // ASem: TSActual:= TSG
            tsGlobal = true;
            // ASem: zona_decl:= true
            zonaDeclaracion = true;
        }

        debug(null);

        return null;

    }

    //Funcion Correspondiente al simbolo no terminal: PR
    //Acciones posibles:
    //      PR -> procedure id A ; D Bloque ;
    private static Atributos PR() {

        Atributos atrID;
        Atributos atrA;
        Atributos atrBloque;
        if (tokenActualCoincideCualquiera("PROCEDURE")) {

            printParse("7");    //    7.  PR -> procedure id A ; D Bloque ;

            //ASin: procedure
            match("PROCEDURE");
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();

            //ASem: TSL:= CrearTS ()
            Procesador.gestorTS.createTSLocal();
            //ASem: TSActual:= TSL
            tsGlobal = false;
            //ASem: despl_local:= 0
            despLocal = 0;

            //ASin: A
            atrA = A();
            //ASin: ;
            match("PYC");

            //ASem: InsertarTipoTS (id.pos, A.tipo→vacío)
            //		InsertarModoTS (id.pos, A.referencia)	
            Procesador.gestorTS.setTipo(idPos, "procedimiento");
            Procesador.gestorTS.setValorAtributoCad(idPos, "tipoRetorno", "vacío");

            if (atrA.getLong() > 0) {
                String[] tipos = atrA.getTipo().split(" ");
                String[] parametros = atrA.getReferencia().split(" ");
                Procesador.gestorTS.setValorAtributoLista(idPos, "tipoParametros", tipos);
                Procesador.gestorTS.setValorAtributoLista(idPos, "pasoParametros", parametros);
                Procesador.gestorTS.setValorAtributoEnt(idPos, "numParametro", atrA.getLong());
            } else {
                Procesador.gestorTS.setValorAtributoEnt(idPos, "numParametro", 0);
            }

            //ASem: InsertarEtiquetaTS (id.pos, nueva_et())
            Procesador.gestorTS.setValorAtributoCad(idPos, "etiqueta", generarEtiqueta());

            //ASin: D
            D();

            //ASem: zona_decl = false
            zonaDeclaracion = false;

            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");

            //ASem:
            // if (Bloque.tipo = tipo_error) 
            // 				then Error ("Error detectado en el desarrollo del cuerpo del Procedure")
            // 			if (Bloque.tipoRet ≠ tipo_ok AND Bloque.tipoRet ≠ vacío) 
            // 				then Error ("Retorno en el procedure incorrecto")
            // 			if (Bloque.exit > 0) 
            // 				then Error ("Exit fuera de bucle detectado en Procedure")
            if (atrBloque.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Error detectado en el desarrollo del cuerpo del PROCEDURE");
            }
            if (!atrBloque.getRet().equals("tipo_ok") && !atrBloque.getRet().equals("vacío") && !atrBloque.getRet().equals("")) {
                GestorError.writeError("semántico", "PROCEDURE con instrucción de retorno no vacío");
            }
            if (atrBloque.getExit() > 0) {
                GestorError.writeError("semántico", "EXIT fuera de bucle detectado en PROCEDURE");
            }

            // ASem: destruirTS (TSL)
            destroyTable(Tabla.LOCAL);
            // ASem: TSActual:= TSG
            tsGlobal = true;
            // ASem: zona_decl:= true
            zonaDeclaracion = true;
        }

        debug(null);

        return null;
    }

    //Funcion Correspondiente al simbolo no terminal: PF
    //Acciones posibles:
    //      PF-> function id A : T ; D Bloque ;
    private static Atributos PF() {

        Atributos atrID;
        Atributos atrA;
        Atributos atrT;
        Atributos atrBloque;
        if (tokenActualCoincideCualquiera("FUNCTION")) {

            printParse("8");    //      PF -> function id A : T ; D Bloque ;

            //ASin: function
            match("FUNCTION");
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();

            //ASem: TSL:= CrearTS ()
            Procesador.gestorTS.createTSLocal();
            //ASem: TSActual:= TSL
            tsGlobal = false;
            //ASem: despl_local:= 0
            despLocal = 0;

            //ASin: A
            atrA = A();
            //ASin: :
            match("DOSPUNTOS");
            //ASin: T
            atrT = T();
            //ASin: ;
            match("PYC");

            //ASem: InsertarTipoTS (id.pos, A.tipo→T.tipo)
            //		InsertarModoTS (id.pos, A.referencia)	
            Procesador.gestorTS.setTipo(idPos, "función");
            Procesador.gestorTS.setValorAtributoCad(idPos, "tipoRetorno", atrT.getTipo());

            if (atrA.getLong() > 0) {
                String[] tipos = atrA.getTipo().split(" ");
                String[] parametros = atrA.getReferencia().split(" ");
                Procesador.gestorTS.setValorAtributoLista(idPos, "tipoParametros", tipos);
                Procesador.gestorTS.setValorAtributoLista(idPos, "pasoParametros", parametros);
                Procesador.gestorTS.setValorAtributoEnt(idPos, "numParametro", atrA.getLong());
            } else {
                Procesador.gestorTS.setValorAtributoEnt(idPos, "numParametro", 0);
            }

            //ASem: InsertarEtiquetaTS (id.pos, nueva_et())
            Procesador.gestorTS.setValorAtributoCad(idPos, "etiqueta", generarEtiqueta());

            //ASin: D
            D();

            //ASem: zona_decl = false
            zonaDeclaracion = false;

            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");

            //ASem:
            // if (Bloque.tipo = tipo_error) 
            // 				then Error ("Hay un error dentro del bloque")
            // 			if (Bloque.tipoRet ≠ tipo_ok AND Bloque.tipoRet ≠ T.tipo) 
            // 				then Error ("Funcion con retorno incorrecto")
            // 			if (Bloque.exit > 0) 
            // 				then Error (exit_fuera_bucle)
            if (atrBloque.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Ha sucedido un error en la función");
            }
            if (!atrBloque.getRet().equals("tipo_ok") && !atrBloque.getRet().equals(atrT.getTipo())) {
                GestorError.writeError("semántico", "Función con retorno no válido");
            }
            if (atrBloque.getExit() > 0) {
                GestorError.writeError("semántico", "EXIT no puede situarse fuera del bucle LOOP");
            }

            //ASem: destruirTS (TSL)
            destroyTable(Tabla.LOCAL);
            //ASem: TSActual:= TSG
            tsGlobal = true;
            //ASem: zona_decl:= true
            zonaDeclaracion = true;
        }

        debug(null);

        return null;
    }

    //Funcion Correspondiente al simbolo no terminal: D
    //Acciones posibles:
    //      	D → var id : T ;
    //          D → λ	
    private static Atributos D() {

        Atributos atrD = new Atributos();
        Atributos atrID;
        Atributos atrT;

        if (tokenActualCoincideCualquiera("VAR")) {
            printParse("9");    //9.	D → var id : T ; DD
            //ASin: var
            match("VAR");
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: :
            match("DOSPUNTOS");
            //ASin: T
            atrT = T();
            //ASin: ;
            match("PYC");

            //ASem:     InsertarTipoTS (id.pos, T.tipo)
            Procesador.gestorTS.setTipo(idPos, atrT.getTipo());
            //ASem: 
            // if (TSActual = TSG) 	then
            // 		{
            // 			InsertarDespTS (id.pos, despl_global)
            // 			despl_global:= despl_global + T.ancho
            // 		}
            // 		else
            // 		{
            // 			InsertarDespTS (id.pos, despl_local)
            // 			despl_local:= despl_local + T.ancho
            // 		}

            if (tsGlobal) {
                Procesador.gestorTS.setValorAtributoEnt(idPos, "desplazamiento", despGlobal);
                despGlobal += atrT.getAncho();
            } else {
                Procesador.gestorTS.setValorAtributoEnt(idPos, "desplazamiento", despLocal);
                despLocal += atrT.getAncho();
            }

            //ASin: DD
            DD();

        } else if (tokenActualCoincideCualquiera("begin", "function", "procedure", "program", "eof")) {

            printParse("10");   //10.	D → λ

            //SIN ACCIONES SINTACTICAS NI SEMANTICAS
        }
        debug(atrD);
        return atrD;
    }

    //Funcion Correspondiente al simbolo no terminal: DD
    //Acciones posibles:
    //      	DD → id : T ;
    //          DD → λ	
    private static Atributos DD() {

        Atributos atrDD = new Atributos();
        Atributos atrID;
        Atributos atrT;

        if (tokenActualCoincideCualquiera("ID")) {

            printParse("11"); //11.	DD → id : T ;

            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: :
            match("DOSPUNTOS");
            //ASin: T
            atrT = T();
            //ASin: ;
            match("PYC");

            //ASem:     InsertarTipoTS (id.pos, T.tipo)
            Procesador.gestorTS.setTipo(idPos, atrT.getTipo());
            //ASem: 
            // if (TSActual = TSG) 	then
            // 		{
            // 			InsertarDespTS (id.pos, despl_global)
            // 			despl_global:= despl_global + T.ancho
            // 		}
            // 		else
            // 		{
            // 			InsertarDespTS (id.pos, despl_local)
            // 			despl_local:= despl_local + T.ancho
            // 		}

            if (tsGlobal) {
                Procesador.gestorTS.setValorAtributoEnt(idPos, "desplazamiento", despGlobal);
                despGlobal += atrT.getAncho();
            } else {
                Procesador.gestorTS.setValorAtributoEnt(idPos, "desplazamiento", despLocal);
                despLocal += atrT.getAncho();
            }

            //ASin: DD
            DD();
        } else if (tokenActualCoincideCualquiera("begin", "function", "procedure", "program", "eof")) {

            printParse("12"); //12.	DD → λ	

            //SIN ACCIONES SINTACTICAS NI SEMANTICAS
        }
        debug(atrDD);
        return atrDD;
    }

    //Funcion Correspondiente al simbolo no terminal: T
    //Acciones posibles:
    //      	T → boolean
    //          T → integer
    //          T → cadena	
    private static Atributos T() {

        Atributos atrT = new Atributos();

        //13.	T → boolean
        if (tokenActualCoincideCualquiera("BOOLEAN")) {

            printParse("13");

            //Asin: boolean
            match("BOOLEAN");

            //Asem: T.tipo:= lógico
            atrT.setTipo("lógico");
            //Asem: T.ancho:= 1
            atrT.setAncho(1);

        } else if (tokenActualCoincideCualquiera("INTEGER")) {

            printParse("14"); //14.	T → integer

            //Asin: integer
            match("INTEGER");

            //Asem: T.tipo:= entero
            atrT.setTipo("entero");
            //Asem: T.ancho:= 1
            atrT.setAncho(1);

        } else if (tokenActualCoincideCualquiera("STRING")) {

            printParse("15");   //15.	T → string	

            //ASin: string
            match("STRING");

            //ASem: T.tipo:= cadena
            atrT.setTipo("cadena");
            //ASem: T.ancho := 64
            atrT.setAncho(64);

        }

        debug(atrT);
        return atrT;

    }

    //Funcion Correspondiente al simbolo no terminal: A
    //Acciones posibles:
    //      	A → ( X id : T AA )
    //          A → λ 
    private static Atributos A() {

        Atributos atrA = new Atributos();
        Atributos atrX;
        Atributos atrID;
        Atributos atrT;
        Atributos atrAA;

        if (tokenActualCoincideCualquiera("PARENT_ABRIR")) {

            printParse("16");   //16.	A → ( X id : T AA )

            //ASin: (
            match("PARENT_ABRIR");
            //ASin: X
            atrX = X();
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: :
            match("DOSPUNTOS");
            //ASin: T
            atrT = T();

            //ASem: InsertarTipoTS (id.pos, T.tipo)
            Procesador.gestorTS.setTipo(idPos, atrT.getTipo());
            //ASem: InsertarDespTS (id.pos, despl_local)
            Procesador.gestorTS.setValorAtributoEnt(idPos, "desplazamiento", despLocal);
            //ASem:if (X.referencia) then
            //  		{
            //  			InsertaAtributoPasoParametrosTS (id.pos, “referencia”)
            //  			despl_local:= despl_local + 1
            //  		}
            //  	else
            //  		{
            //  			InsertaAtributoPasoParametrosTS (id.pos, “valor”)
            //  			despl_local:= despl_local + T.ancho
            //  		} 

            if (atrX.getReferencia().equals("referencia")) {
                despLocal += 1;
                Procesador.gestorTS.setValorAtributoEnt(idPos, "modoParametro", 1);

            } else if (atrX.getReferencia().equals("valor")) {    //valor
                despLocal += atrT.getAncho();
                Procesador.gestorTS.setValorAtributoEnt(idPos, "modoParametro", 0);
            }

            //ASin: AA
            atrAA = AA();
            //ASin: )
            match("PARENT_CERRAR");

            //ASem: if (AA.tipo ≠ vacío) then 
            //		{
            //			A.tipo:= T.tipo x AA.tipo
            //			A.referencia:= X.referencia x AA.referencia  // producto cartesiano de enteros
            //		    A.long:= AA.long + 1
            //          } 
            //        else 
            //		{
            //			A.tipo:= T.tipo
            //			A.referencia:= X.referencia 
            //			A.long:= 1
            //		}
            if (atrAA.getTipo().equals("vacío")) {
                atrA.setTipo(atrT.getTipo());
                atrA.setReferencia(atrX.getReferencia());
                atrA.setLong(1);

            } else {
                atrA.setTipo(atrT.getTipo() + " " + atrAA.getTipo());
                atrA.setReferencia(atrX.getReferencia() + " " + atrAA.getReferencia());
                atrA.setLong(atrAA.getLong() + 1);
            }

        } else if (tokenActualCoincideCualquiera("DOSPUNTOS", "PYC")) {

            printParse("17");   //17.	A → λ 
            //ASem: A.tipo = vacío
            atrA.setTipo("vacío");
            //ASem: A.long = 0
            atrA.setLong(0);
            //ASem: A.referencia = ""
            atrA.setReferencia("");
        }
        debug(atrA);
        return atrA;
    }

    //Funcion Correspondiente al simbolo no terminal: AA
    //Acciones posibles:
    //      	AA → ; X id : T AA1 
    //          AA → λ
    private static Atributos AA() {

        Atributos atrAA = new Atributos();
        Atributos atrID;
        Atributos atrT;
        Atributos atrX;
        Atributos atrAA1;

        if (tokenActualCoincideCualquiera("PYC")) {

            printParse("18");   //18.	AA → ; X id : T AA1 
            //ASin: ;
            match("PYC");
            //ASin: X
            atrX = X();
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: :
            match("DOSPUNTOS");
            //ASin: T
            atrT = T();

            //ASem: InsertarTipoTS (id.pos, T.tipo)
            Procesador.gestorTS.setTipo(idPos, atrT.getTipo());
            //ASem: InsertarDespTS (id.pos, despl_local)
            Procesador.gestorTS.setValorAtributoEnt(idPos, "desplazamiento", despLocal);
            //ASem:if (X.referencia) then
            //  		{
            //  			InsertaAtributoPasoParametrosTS (id.pos, “referencia”)
            //  			despl_local:= despl_local + 1
            //  		}
            //  	else
            //  		{
            //  			InsertaAtributoPasoParametrosTS (id.pos, “valor”)
            //  			despl_local:= despl_local + T.ancho
            //  		} 

            if (atrX.getReferencia().equals("referencia")) {
                despLocal += 1;
                Procesador.gestorTS.setValorAtributoEnt(idPos, "modoParametro", 1);

            } else if (atrX.getReferencia().equals("valor")) {    //valor
                despLocal += atrT.getAncho();
                Procesador.gestorTS.setValorAtributoEnt(idPos, "modoParametro", 0);
            }

            //ASin: AA1
            atrAA1 = AA();

            //ASem: if (AA.tipo ≠ vacío) then 
            //		{
            //			A.tipo:= T.tipo x AA.tipo
            //			A.referencia:= X.referencia x AA.referencia  // producto cartesiano de lógicos
            //		    A.long:= AA.long + 1
            //      } 
            //        else 
            //		{
            //			A.tipo:= T.tipo
            //			A.referencia:= X.referencia 
            //			A.long:= 1
            //		}
            if (atrAA1.getTipo().equals("vacío")) {
                atrAA.setTipo(atrT.getTipo());
                atrAA.setReferencia(atrX.getReferencia());
                atrAA.setLong(1);
            } else {
                atrAA.setTipo(atrT.getTipo() + " " + atrAA1.getTipo());
                atrAA.setReferencia(atrX.getReferencia() + " " + atrAA1.getReferencia());
                atrAA.setLong(atrAA1.getLong() + 1);
            }

        } else if (tokenActualCoincideCualquiera("PARENT_CERRAR")) {

            printParse("19"); //19.	AA → λ
            //ASem: AA.tipo = vacío
            atrAA.setTipo("vacío");
            //ASem: AA.long = 0
            atrAA.setLong(0);
            //ASem: AA.referencia = ""
            atrAA.setReferencia("");
        }
        debug(atrAA);
        return atrAA;
    }

    //Funcion Correspondiente al simbolo no terminal: X
    //Acciones posibles:
    //      	X → var
    //          X → λ
    private static Atributos X() {

        Atributos atrX = new Atributos();

        if (tokenActualCoincideCualquiera("VAR")) {

            printParse("20");       //20.	X → var
            //ASin: var
            match("VAR");
            //Asem: X.referencia:= true
            atrX.setReferencia("referencia");

        } else if (tokenActualCoincideCualquiera("ID")) {

            printParse("21");       //21.	X → λ	

            //Asem: X.referencia:= false
            atrX.setReferencia("valor");
        }
        debug(atrX);
        return atrX;
    }

    //Funcion Correspondiente al simbolo no terminal: C 
    //Acciones posibles:
    //      	C → B C1
    //          C → λ
    private static Atributos C() {

        Atributos atrC = new Atributos();

        if (tokenActualCoincideCualquiera("CASE", "EXIT", "FOR", "ID", "IF", "LOOP", "READ", "REPEAT", "RETURN", "WHILE", "WRITE", "WRITELN")) {
            printParse("22");   //22.	C → B C1

            //Asin: B
            Atributos atrB = B();
            //Asin: C1
            Atributos atrC1 = C();

            //Asem: 
            //if (B.tipo = tipo_ok){ 
            //    C.tipo:=C1.tipo 
            // } else{ 
            //      C.tipo:= tipo_error 
            // } 
            if (atrB.getTipo().equals("tipo_ok")) {
                atrC.setTipo(atrC1.getTipo());
            } else {
                atrC.setTipo("tipo_error");
            }

            //ASem: C.exit:= B.exit + C1.exit
            atrC.setExit(atrB.getExit() + atrC1.getExit());

            //ASem: C.tipoRet:=	if (B.tipoRet = C1.tipoRet)
            //        				then B.tipoRet
            //        			else if (B.tipoRet = tipo_ok)
            //        				then C1.tipoRet
            //        			else if (C1.tipoRet = tipo_ok)
            //        				then B.tipoRet
            //        			else tipo_error
            if (atrB.getRet().equals(atrC1.getRet())) {
                atrC.setRet(atrB.getRet());
            } else if (atrB.getRet().equals("tipo_ok")) {
                atrC.setRet(atrC1.getRet());
            } else if (atrC1.getRet().equals("tipo_ok")) {
                atrC.setRet(atrB.getRet());
            } else {
                atrC.setRet("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("END", "UNTIL")) {

            printParse("23");   //23.	C → λ	
            //ASem: C.tipo:= tipo_ok
            atrC.setTipo("tipo_ok");
            //ASem: C.exit:= 0
            atrC.setExit(0);
            //ASem: C.tipoRet:= tipo_ok
            atrC.setRet("tipo_ok");
        }
        debug(atrC);
        return atrC;
    }

    //Funcion Correspondiente al simbolo no terminal: Bloque
    //Acciones posibles:
    //      	24.	Bloque → begin C end
    private static Atributos Bloque() {

        Atributos atrBloque = new Atributos();
        Atributos atrC;
        if (tokenActualCoincideCualquiera("BEGIN")) {

            printParse("24");   //24.	Bloque → begin C end
            //ASin: begin
            match("BEGIN");
            //ASin: C
            atrC = C();
            //ASin: end
            match("END");
            //ASem: Bloque.tipo:= C.tipo
            atrBloque.setTipo(atrC.getTipo());
            //ASem: Bloque.exit:= C.exit
            atrBloque.setExit(atrC.getExit());
            //ASem: Bloque.tipoRet:= C.tipoRet
            atrBloque.setRet(atrC.getRet());
        }

        debug(atrBloque);
        return atrBloque;
    }

    //Funcion Correspondiente al simbolo no terminal: B
    //Acciones posibles:
    //      	B → S	
    //          B → if E then Bcola
    //          B → while  E do Bloque ;
    //          B → repeat C until E ;
    //          B → loop C end ;
    //          B → for id := E1 to E2 do Bloque ;
    //          B → case E of N O end;
    private static Atributos B() {

        Atributos atrB = new Atributos();
        Atributos atrS;
        Atributos atrE;
        Atributos atrE1;
        Atributos atrE2;
        Atributos atrBcola;
        Atributos atrBloque;
        Atributos atrC;
        Atributos atrN;
        Atributos atrO;

        if (tokenActualCoincideCualquiera("EXIT", "ID", "READ", "RETURN", "WRITE", "WRITELN")) {

            printParse("25");       //25.	B → S	
            //ASin: S	
            atrS = S();
            //ASem: B.tipo:= S.tipo 
            atrB.setTipo(atrS.getTipo());
            //ASem: B.exit:= S.exit
            atrB.setExit(atrS.getExit());
            //ASem: B.tipoRet:= S.tipoRet
            atrB.setRet(atrS.getRet());

        } else if (tokenActualCoincideCualquiera("IF")) {

            printParse("26");   //26.	B → if E then Bcola	
            //ASin: if
            match("IF");
            //ASin: E
            atrE = E();
            //ASin: then
            match("THEN");
            //Asin: Bcola
            atrBcola = Bcola();
            //ASem:
            // if (E.tipo = lógico) { 
            //        B.tipo:= Bcola.tipo  
            // } else { 
            //    B.tipo:= tipo_error  
            // } 
            if (atrE.getTipo().equals("lógico")) {
                atrB.setTipo(atrBcola.getTipo());
            } else {
                atrB.setTipo("tipo_error");
            }
            //ASem: B.exit:= Bcola.exit
            atrB.setExit(atrBcola.getExit());
            //ASem: B.tipoRet:= Bcola.tipoRet
            atrB.setRet(atrBcola.getRet());

        } else if (tokenActualCoincideCualquiera("WHILE")) {

            printParse("31");       //31.	B → while  E do Bloque ;	

            //ASin: while
            match("WHILE");
            //ASin: E
            atrE = E();
            //ASin: do
            match("DO");
            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");
            //ASem: 
            // if (E.tipo = lógico) {
            //        B.tipo:= Bloque.tipo  
            // } else { 
            //    B.tipo:= tipo_error  
            // }    
            if (atrE.getTipo().equals("lógico")) {
                atrB.setTipo(atrBloque.getTipo());
            } else {
                atrB.setTipo("tipo_error");
            }
            //ASem: B.exit:= Bloque.exit
            atrB.setExit(atrBloque.getExit());
            //ASem: B.tipoRet:= Bloque.tipoRet
            atrB.setRet(atrBloque.getRet());

        } else if (tokenActualCoincideCualquiera("REPEAT")) {

            printParse("32");   //32.	B → repeat C until E ;
            //ASin: repeat
            match("REPEAT");
            //ASin: C
            atrC = C();
            //ASin: until
            match("UNTIL");
            //ASin: E
            atrE = E();
            //ASin: ;
            match("PYC");

            //ASem: 
            // if (E.tipo = lógico) {
            //        B.tipo:= C.tipo  
            // } else { 
            //    B.tipo:= tipo_error  
            // }
            if (atrE.getTipo().equals("lógico")) {
                atrB.setTipo(atrC.getTipo());
            } else {
                atrB.setTipo("tipo_error");
            }
            //ASem: B.exit:= C.exit
            atrB.setExit(atrC.getExit());
            //ASem: B.tipoRet:= C.tipoRet
            atrB.setRet(atrC.getRet());

        } else if (tokenActualCoincideCualquiera("LOOP")) {

            printParse("33");       //33.	B → loop C end ;

            //ASin: loop
            match("LOOP");
            //ASin: C
            atrC = C();
            //ASin: end
            match("END");
            //ASin: ;
            match("PYC");
            //ASem: if (C.exit ≠ 1) 
            //			then Error (“dentro de loop debe haber 1 y solo 1 exit”)
            if (atrC.getExit() > 1) {
                GestorError.writeError("semántico", "Dentro de un LOOP no debe haber más de un EXIT");
            } else if (atrC.getExit() < 1) {
                GestorError.writeError("semántico", "Dentro de un LOOP debe haber un EXIT");
            }

            //ASem: B.tipo:= C.tipo
            atrB.setTipo(atrC.getTipo());
            //ASem: B.exit := 0
            atrB.setExit(0);
            //ASem: B.tipoRet := C.tipoRet
            atrB.setRet(atrC.getRet());

        } else if (tokenActualCoincideCualquiera("FOR")) {
            Atributos atrID;

            printParse("34");   //34.	B → for id := E1 to E2 do Bloque ;	

            //ASin: for
            match("FOR");
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: :=
            match("ASIGNACION");
            //ASin: E1
            atrE1 = E();
            //ASin: to
            match("TO");
            //ASin: E2
            atrE2 = E();
            //ASin: do
            match("DO");
            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");
            //ASem: 
            // if (E1.tipo = entero AND E2.tipo = entero AND TSActual[id].tipo = entero) 
            //			B.tipo:= Bloque.tipo
            //  else
            //          B.tipo:= tipo_error
            if (atrE1.getTipo().equals("entero") && atrE2.getTipo().equals("entero")
                    && Procesador.gestorTS.getTipo(idPos).equals("entero")) {
                atrB.setTipo(atrBloque.getTipo());
            } else {
                atrB.setTipo("tipo_error");
            }
            //ASem: B.exit:= Bloque.exit
            atrB.setExit(atrBloque.getExit());
            //ASem: B.tipoRet:= Bloque.tipoRet
            atrB.setRet(atrBloque.getRet());
        } else if (tokenActualCoincideCualquiera("CASE")) {

            printParse("35");       //35.	B → case E of N O end;

            //ASin: case
            match("CASE");
            //ASin: E
            atrE = E();
            //ASin: of
            match("OF");
            //ASin: N
            atrN = N();
            //ASin: O
            atrO = O();
            //ASin: end
            match("END");
            //ASin: ;
            match("PYC");
            //ASem: 
            // if (E.tipo = entero AND N.tipo = O.tipo = tipo_ok) 
            //     B.tipo:= tipo_ok 
            //   else  
            //     B.tipo:= tipo_error 
            if (atrE.getTipo().equals("entero") && atrN.getTipo().equals("tipo_ok")
                    && atrO.getTipo().equals("tipo_ok")) {
                atrB.setTipo("tipo_ok");
            } else {
                atrB.setTipo("tipo_error");
            }
            //ASem: B.exit:= N.exit + O.exit
            atrB.setExit(atrN.getExit() + atrO.getExit());
            //ASem: 
            // if (N.tipoRet = O.tipoRet) 
            //     B.tipoRet:= N.tipoRet 
            // else if (N.tipoRet = tipo_ok) 
            //     B.tipoRet:= O.tipoRet 
            // else if (O.tipoRet = tipo_ok) 
            //     B.tipoRet:= N.tipoRet 
            // else  
            //     B.tipoRet:= tipo_error 

            if (atrN.getRet().equals(atrO.getRet())) {
                atrB.setRet(atrN.getRet());
            } else if (atrN.getRet().equals("tipo_ok")) {
                atrB.setRet(atrO.getRet());
            } else if (atrO.getRet().equals("tipo_ok")) {
                atrB.setRet(atrN.getRet());
            } else {
                atrB.setRet("tipo_error");
            }
        }
        debug(atrB);
        return atrB;
    }

    //Funcion Correspondiente al simbolo no terminal: Bcola
    //Acciones posibles:
    //      	Bcola → S
    //          Bcola → Bloque ; BlqElse
    private static Atributos Bcola() {

        Atributos atrBcola = new Atributos();
        Atributos atrS;
        Atributos atrBloque;
        Atributos atrBlqElse;
        if (tokenActualCoincideCualquiera("EXIT", "ID", "READ", "RETURN", "WRITE", "WRITELN")) {

            printParse("27");   //27.	Bcola → S	
            //ASin: S
            atrS = S();
            //ASem: B.tipo = S.tipo
            atrBcola.setTipo(atrS.getTipo());
            //ASem: B.exit = S.exit
            atrBcola.setExit(atrS.getExit());
            //ASem: B.tipoRet = S.tipoRet
            atrBcola.setRet(atrS.getRet());

        } else if (tokenActualCoincideCualquiera("BEGIN")) {

            printParse("28");       //28.	Bcola → Bloque ; BlqElse	
            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");
            //ASin: BlqElse
            atrBlqElse = BlqElse();
            //ASem: 
            // if(Bloque.tipo = tipo_ok) { 
            //     Bcola.tipo:= BlqElse.tipo 
            //    } else { 
            //     Bcola.tipo:= tipo_error 
            //   } 
            if (atrBloque.getTipo().equals("tipo_ok")) {
                atrBcola.setTipo(atrBlqElse.getTipo());
            } else {
                atrBcola.setTipo("tipo_error");
            }
            //  ASem: Bcola.exit:= Bloque.exit + BlqElse.exit
            atrBcola.setExit(atrBloque.getExit() + atrBlqElse.getExit());
            //ASem: Bcola.tipoRet:=	if (Bloque.tipoRet = BlqElse.tipoRet)
            //                          then Bloque.tipoRet
            //                      else if (Bloque.tipoRet = tipo_ok)
            //                          then BlqElse.tipoRet
            //                      else if (BlqElse.tipoRet = tipo_ok)
            //                          then Bloque.tipoRet
            //                      else tipo_error
            if (atrBloque.getRet().equals(atrBlqElse.getRet())) {
                atrBcola.setRet(atrBloque.getRet());
            } else if (atrBloque.getRet().equals("tipo_ok")) {
                atrBcola.setRet(atrBlqElse.getRet());
            } else if (atrBlqElse.getRet().equals("tipo_ok")) {
                atrBcola.setRet(atrBloque.getRet());
            } else {
                atrBcola.setRet("tipo_error");
            }

        }

        debug(atrBcola);
        return atrBcola;
    }

    //Funcion Correspondiente al simbolo no terminal: BlqElse
    //Acciones posibles:
    //      	BlqElse → else Bloque ;
    //          BlqElse → λ
    private static Atributos BlqElse() {

        Atributos atrBlqElse = new Atributos();
        Atributos atrBloque;

        if (tokenActualCoincideCualquiera("ELSE")) {

            printParse("29");   //29.	BlqElse → else Bloque ;	

            //ASin: else
            match("ELSE");
            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");
            //ASem:	BlqElse.tipo = Bloque.tipo
            atrBlqElse.setTipo(atrBloque.getTipo());
            //ASem:	BlqElse.exit = Bloque.exit
            atrBlqElse.setExit(atrBloque.getExit());
            //ASem:	BlqElse.tipoRet = Bloque.tipoRet
            atrBlqElse.setRet(atrBloque.getRet());

        } else if ((tokenActualCoincideCualquiera("CASE", "END", "EXIT", "FOR", "ID", "IF", "LOOP", "READ", "REPEAT", "RETURN", "UNTIL", "WHILE", "WRITE", "WRITELN"))) {

            printParse("30");       //30.	BlqElse → λ 	

            //ASem: BlqElse.tipo = tipo_ok
            atrBlqElse.setTipo("tipo_ok");
            //ASem: BlqElse.exit = 0
            atrBlqElse.setExit(0);
            //ASem: BlqElse.tipoRet = tipo_ok
            atrBlqElse.setRet("tipo_ok");
        }
        debug(atrBlqElse);
        return atrBlqElse;

    }

    //Funcion Correspondiente al simbolo no terminal: N
    //Acciones posibles:
    //      	N → entero : Bloque ; N1
    //          N → λ
    private static Atributos N() {

        Atributos atrN = new Atributos();
        Atributos atrBloque;
        Atributos atrN1;

        if (tokenActualCoincideCualquiera("ENTERO")) {

            printParse("36");     //36.	N → entero : Bloque ; N1
            //ASin: entero
            match("ENTERO");
            //ASin: :
            match("DOSPUNTOS");
            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");
            //ASin: N1
            atrN1 = N();
            //ASem: N.tipo:=	if (N1.tipo = tipo_ok)
            //						then Bloque.tipo
            //					else tipo_error
            if (atrN1.getTipo().equals("tipo_ok")) {
                atrN.setTipo(atrBloque.getTipo());
            } else {
                atrN.setTipo("tipo_error");
            }
            //ASem: N.exit:= Bloque.exit + N1.exit
            atrN.setExit(atrBloque.getExit() + atrN1.getExit());
            //ASem: N.tipoRet:=	if (Bloque.tipoRet = N1.tipoRet)
            //                  	then Bloque.tipoRet
            //					else if (Bloque.tipoRet = tipo_ok)
            //						then N1.tipoRet
            //					else if (N1.tipoRet = tipo_ok)
            //						then Bloque.tipoRet
            //					else tipo_error
            if (atrBloque.getRet().equals(atrN1.getRet())) {
                atrN.setRet(atrBloque.getRet());
            } else if (atrBloque.getRet().equals("tipo_ok")) {
                atrN.setRet(atrN1.getRet());
            } else if (atrN1.getRet().equals("tipo_ok")) {
                atrN.setRet(atrBloque.getRet());
            } else {
                atrN.setRet("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("END", "OTHERWISE")) {

            printParse("37");       //37.	N → λ	
            //Asem: N.tipo = tipo_ok
            atrN.setTipo("tipo_ok");
            //ASem: N.exit = 0
            atrN.setExit(0);
            //ASem: N.tipoRet = tipo_ok
            atrN.setRet("tipo_ok");
        }
        debug(atrN);
        return atrN;
    }

    //Funcion Correspondiente al simbolo no terminal: O
    //Acciones posibles:
    //      	O → otherwise : Bloque ;
    //          O → λ
    private static Atributos O() {

        Atributos atrO = new Atributos();
        Atributos atrBloque;

        if (tokenActualCoincideCualquiera("OTHERWISE")) {

            printParse("38");       //38.	O → otherwise : Bloque ;		

            //ASin: otherwise
            match("OTHERWISE");
            //ASin: :
            match("DOSPUNTOS");
            //ASin: Bloque
            atrBloque = Bloque();
            //ASin: ;
            match("PYC");
            //ASem:	O.tipo = Bloque.tipo
            atrO.setTipo(atrBloque.getTipo());
            //ASem:	O.exit = Bloque.exit
            atrO.setExit(atrBloque.getExit());
            //ASem:	O.tipoRet = Bloque.tipoRet
            atrO.setRet(atrBloque.getRet());

        } else if (tokenActualCoincideCualquiera("END")) {

            printParse("39");       //39.	O → λ	 
            //ASem:	O.tipo = tipo_ok
            atrO.setTipo("tipo_ok");
            //ASem:	O.exit = 0
            atrO.setExit(0);
            //ASem:	O.tipoRet = tipo_ok
            atrO.setRet("tipo_ok");
        }
        debug(atrO);
        return atrO;

    }

    //Funcion Correspondiente al simbolo no terminal: S
    //Acciones posibles:
    //      	S → write LL ;
    //          S → writeln LL ;
    //          S → read ( V ) ;
    //          S → id Scola
    //          S → return Y ;
    //          S → exit when E ;
    private static Atributos S() {

        Atributos atrS = new Atributos();
        Atributos atrLL;
        Atributos atrV;
        Atributos atrY;
        Atributos atrScola;
        Atributos atrE;

        if (tokenActualCoincideCualquiera("WRITE")) {

            printParse("40"); //40.	S → write LL ;

            //ASin: write
            match("WRITE");
            //ASin: LL
            atrLL = LL();
            //ASin: ;
            match("PYC");

            //ASem: S.tipo:= tipo_ok
            atrS.setTipo("tipo_ok");

            //ASem: if (LL.tipo ≠ vacío) then
            //		{
            //			for i:= 1 to LL.long
            //				if (LL.tipo[i] ≠ entero AND LL.tipo[i] ≠ cadena)
            //					then S.tipo:= tipo_error
            //		}
            if (!atrLL.getTipo().equals("vacío")) {
                String[] lista = atrLL.getTipo().split(" ");
                for (String item : lista) {
                    if (!item.equals("entero") && !item.equals("cadena")) {
                        atrS.setTipo("tipo_error");
                    }
                }
            }
            if (atrS.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Error en los argumentos de WRITE");

            }
            //ASem:	S.exit:= 0
            atrS.setExit(0);
            //ASem: S.tipoRet:= tipo_ok
            atrS.setRet("tipo_ok");

        } else if (tokenActualCoincideCualquiera("WRITELN")) {

            printParse("41");  //41.	S → writeln LL ;

            //ASin: writeln
            match("WRITELN");
            //ASin: LL
            atrLL = LL();
            //ASin: ;
            match("PYC");
            //ASem: S.tipo:= tipo_ok
            atrS.setTipo("tipo_ok");

            //ASem: if (LL.tipo ≠ vacío) then
            //		{
            //			for i:= 1 to LL.long
            //				if (LL.tipo[i] ≠ entero AND LL.tipo[i] ≠ cadena)
            //					then S.tipo:= tipo_error
            //		}
            if (!atrLL.getTipo().equals("vacío")) {
                String[] lista = atrLL.getTipo().split(" ");
                for (String item : lista) {
                    if (!item.equals("entero") && !item.equals("cadena")) {
                        atrS.setTipo("tipo_error");

                    }
                }
            }
            if (atrS.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Error en los argumentos de WRITELN");

            }
            //ASem:	S.exit:= 0
            atrS.setExit(0);
            //ASem: S.tipoRet:= tipo_ok
            atrS.setRet("tipo_ok");

        } else if (tokenActualCoincideCualquiera("READ")) {

            printParse("42"); //42.	S → read ( V ) ;

            //ASin: read
            match("READ");
            //ASin: (
            match("PARENT_ABRIR");
            //ASin: V
            atrV = V();
            //ASin: )
            match("PARENT_CERRAR");
            //ASin: ;
            match("PYC");

            //ASem: S.tipo:= tipo_ok
            atrS.setTipo("tipo_ok");

            //ASem: if (V.tipo ≠ vacío) then
            //		{
            //			for i:= 1 to V.long
            //				if (V.tipo[i] ≠ entero AND V.tipo[i] ≠ cadena)
            //					then S.tipo:= tipo_error
            //		}
            if (!atrV.getTipo().equals("vacío")) {
                String[] lista = atrV.getTipo().split(" ");
                for (String item : lista) {
                    if (!item.equals("entero") && !item.equals("cadena")) {
                        atrS.setTipo("tipo_error");

                    }
                }
            }

            if (atrS.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Error en los argumentos de READ");

            }
            //ASem:	S.exit:= 0
            atrS.setExit(0);
            //ASem: S.tipoRet:= tipo_ok
            atrS.setRet("tipo_ok");

        } else if (tokenActualCoincideCualquiera("ID")) {
            Atributos atrId;

            printParse("43"); //43.	S → id Scola

            //ASin: id
            atrId = match("ID");
            int idPos = atrId.getPos();
            //ASin: Scola
            atrScola = Scola();
            //ASem : id.tipo:= BuscaTipoTS (id.pos) 
            atrId.setTipo(Procesador.gestorTS.getTipo(idPos));
            //ASem: S.tipo:=	if (id.tipo = Scola.tipo)
            //                        then tipo_ok
            //                    else if (id.tipo = Scola.tipo→vacío)
            //                        then tipo_ok
            //                    else tipo_error 

            String idEtiq = "";
            if (atrId.getTipo().equals("procedimiento")) {
                idEtiq = Procesador.gestorTS.getValorAtributoCad(idPos, "etiqueta");
            }

            if (atrId.getTipo().equals(atrScola.getTipo()) && atrScola.getAsig() && !atrId.getTipo().equals("procedimiento")) {    //es asignacion y son iguales int-int
                atrS.setTipo("tipo_ok");
            } else if (atrId.getTipo().equals("procedimiento") && !atrScola.getAsig() && !idEtiq.equals("main")) {    //es llamada a procedimiento y no es MAIN 
                int numParam = Procesador.gestorTS.getValorAtributoEnt(idPos, "numParametro");
                if (numParam > 0) {
                    String[] sColaAtributos = atrScola.getTipo().split(" ");
                    String[] idAtbAtributos = Procesador.gestorTS.getValorAtributoLista(idPos, "tipoParametros");
                    if (sColaAtributos.length == idAtbAtributos.length && Arrays.compare(sColaAtributos, idAtbAtributos) == 0) {
                        atrS.setTipo("tipo_ok");
                    } else {
                        GestorError.writeError("semántico", "Los parámetros del PROCEDURE no coinciden.\n \t Se ha recibido: "
                                + Arrays.toString(sColaAtributos) + "\n \t y se esperaba: " + Arrays.toString(idAtbAtributos));
                        atrS.setTipo("tipo_error");
                    }
                } else if (numParam == 0 && !atrScola.getAsig()) {
                    String[] sColaAtributos = atrScola.getTipo().split(" ");

                    GestorError.writeError("semántico", "Los parámetros del PROCEDURE no coinciden" + "\n \t Se ha recibido: "
                            + Arrays.toString(sColaAtributos) + "\n \t pero el PROCEDURE no tiene parámetros ");
                    atrS.setTipo("tipo_error");
                } else {
                    atrS.setTipo("tipo_ok");
                }
            } else if (idEtiq.equals("main")) {      // llamada a programa principal
                GestorError.writeError("semántico", "No se puede realizar una llamada al programa principal");
                atrS.setTipo("tipo_error");
            } else if (atrId.getTipo().equals("procedimiento") && atrScola.getAsig()) {
                GestorError.writeError("semántico", "Se está realizando una asignación a un PROCEDURE");
                atrS.setTipo("tipo_error");
            } else if (Procesador.gestorTS.getTipo(idPos).equals("función")) {     //llamada a una funcion
                atrS.setTipo("tipo_error");
                GestorError.writeError("semántico", "Una funcíón no puede ser llamada como sentencia");
            } else {
                atrS.setTipo("tipo_error");
                if (atrScola.getAsig()) {
                    GestorError.writeError("semántico", "Tipos incompatibles en asignación");
                } else {
                    GestorError.writeError("semántico", "Error en llamada a procedimiento");
                }
            }

            atrS.setExit(0);
            atrS.setRet("tipo_ok");

            //S.exit:= 0
            atrS.setExit(0);
            //S.tipoRet:= tipo_ok
            atrS.setRet("tipo_ok");

        } else if (tokenActualCoincideCualquiera("RETURN")) {

            printParse("46"); //46.	S → return Y ;

            //ASin: return
            match("RETURN");
            //ASin: Y
            atrY = Y();
            //ASin: ;
            match("PYC");
            //ASem: S.tipo:=	if (Y.tipo ≠ tipo _error)
            //        				then tipo_ok
            //        			else tipo_error 
            if (!atrY.getTipo().equals("tipo_error")) {
                atrS.setTipo("tipo_ok");
            } else {
                atrS.setTipo("tipo_error");

            }
            //ASem: S.exit:= 0
            atrS.setExit(0);
            //ASem: S.tipoRet:= Y.tipo
            atrS.setRet(atrY.getTipo());

        } else if (tokenActualCoincideCualquiera("EXIT")) {

            printParse("47"); //47.	S → exit when E ;

            //ASin: exit
            match("EXIT");
            //ASin: when
            match("WHEN");
            //ASin: E
            atrE = E();
            //ASin: ;
            match("PYC");
            //ASem: S.tipo:=	if (E.tipo = lógico)
            //                        then tipo_ok
            //                    else tipo_error
            if (atrE.getTipo().equals("lógico")) {
                atrS.setTipo("tipo_ok");
            } else {
                atrS.setTipo("tipo_error");
            }
            //ASem: S.exit:= 1
            atrS.setExit(1);
            //ASem: S.tipoRet:= tipo_ok
            atrS.setRet("tipo_ok");
        }

        debug(atrS);
        return atrS;
    }
    //Funcion Correspondiente al simbolo no terminal: Scola
    //Acciones posibles:

    //      	Scola → := E ;
    //          Scola → LL ;
    private static Atributos Scola() {

        Atributos atrScola = new Atributos();
        Atributos atrE;
        Atributos atrLL;

        if (tokenActualCoincideCualquiera("ASIGNACION")) {

            printParse("44"); //44.	Scola → := E ;

            //ASin: :=
            match("ASIGNACION");
            //ASin: E
            atrE = E();
            //ASin: ;
            match("PYC");
            //ASem: Scola.tipo = E.tipo
            atrScola.setTipo(atrE.getTipo());
            //ASem: Scola.asig = true
            atrScola.setAsig(true);

        } else if (tokenActualCoincideCualquiera("PARENT_ABRIR", "PYC")) {

            printParse("45"); //45.	Scola → LL ;

            //ASin: LL
            atrLL = LL();
            //ASin: ;
            match("PYC");
            //ASem: Scola.tipo = LL.tipo
            atrScola.setTipo(atrLL.getTipo());
            //ASem: Scola.long = LL.long
            atrScola.setLong(atrLL.getLong());
            //ASem: Scola.asig = false
            atrScola.setAsig(false);
        }
        debug(atrScola);
        return atrScola;
    }

    //Funcion Correspondiente al simbolo no terminal: LL
    //Acciones posibles:
    //      	LL → ( L ) 
    //          LL → λ
    private static Atributos LL() {

        Atributos atrLL = new Atributos();
        Atributos atrL;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR")) {

            printParse("48");   //48.	LL → ( L )  

            //ASin: (
            match("PARENT_ABRIR");
            //ASin: L
            atrL = L();
            //ASin: )
            match("PARENT_CERRAR");

            //ASem: LL.tipo = L.tipo
            atrLL.setTipo(atrL.getTipo());
            //ASem: LL.long = L.long
            atrLL.setLong(atrL.getLong());
        } else if (tokenActualCoincideCualquiera("PARENT_CERRAR", "PRODUCTO", "POTENCIA", "MAS", "COMA", "MENOS", "DIVISION", "PYC", "MAYOR", "MAYOR_IGUAL",
                "MENOR", "MENOR_IGUAL", "IGUAL", "DISTINTO", "AND", "DO", "IN", "MOD", "OF", "OR", "THEN", "TO", "XOR")) {  //lambda

            printParse("49");   //49.    LL → λ
            //Asem: LL.tipo = "vacío"
            atrLL.setTipo("vacío");
            //ASem: LL.long = 0
            atrLL.setLong(0);
        }
        debug(atrLL);
        return atrLL;
    }

    //Funcion Correspondiente al simbolo no terminal: L
    //Acciones posibles:
    //      	L → E Q
    private static Atributos L() {

        Atributos atrL = new Atributos();
        Atributos atrE;
        Atributos atrQ;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("50"); //50.	L → E Q
            //ASin: E
            atrE = E();
            //ASin: Q
            atrQ = Q();
            //ASem:
            // if (Q.tipo = vacío) 
            //     L.tipo:= E.tipo 
            // else 
            //     L.tipo:= E.tipo x Q.tipo 

            if (atrQ.getTipo().equals("vacío")) {
                atrL.setTipo(atrE.getTipo());
            } else {
                atrL.setTipo(atrE.getTipo() + " " + atrQ.getTipo());
            }
            //ASem: L.long:= 1 + Q.long
            atrL.setLong(atrQ.getLong() + 1);

        }
        debug(atrL);
        return atrL;
    }

    //Funcion Correspondiente al simbolo no terminal: Q
    //Acciones posibles:
    //      	Q → , E Q
    //          Q → λ
    private static Atributos Q() {

        Atributos atrQ = new Atributos();
        Atributos atrE;
        Atributos atrQ1;
        if (tokenActualCoincideCualquiera("COMA")) {

            printParse("51");   //51.	Q → , E Q1
            //ASin: ,
            match("COMA");
            //ASin: E
            atrE = E();
            //ASin: Q
            atrQ1 = Q();
            //ASem:
            //  if (Q1.tipo = vacío)  
            //      Q.tipo:= E.tipo 
            //  else 
            //      Q.tipo:= E.tipo x Q1.tipo 
            if (atrQ1.getTipo().equals("vacío")) {
                atrQ.setTipo(atrE.getTipo());
            } else {
                atrQ.setTipo(atrE.getTipo() + " " + atrQ1.getTipo());
            }

            //ASem: Q.long:= 1 + Q1.long
            atrQ.setLong(atrQ1.getLong());

        } else if (tokenActualCoincideCualquiera("PARENT_CERRAR")) {

            printParse("52"); // 52.	Q → λ
            //ASem: Q.tipo := vacío
            atrQ.setTipo("vacío");
            //ASem: Q.long := 0
            atrQ.setLong(0);

        }
        debug(atrQ);
        return atrQ;
    }

    //Funcion Correspondiente al simbolo no terminal: V
    //Acciones posibles:
    //      	V → id W
    private static Atributos V() {

        Atributos atrID;
        Atributos atrV = new Atributos();
        Atributos atrW;

        if (tokenActualCoincideCualquiera("ID")) {

            printParse("53");   //53.	V → id W
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: W
            atrW = W();
            //ASem: V.tipo:=	if (W.tipo = vacío)
            //      				then buscaTipoTS (id.pos)
            //      			else buscaTipoTS (id.pos) x W.tipo

            if (atrW.getTipo().equals("vacío")) {
                atrV.setTipo(Procesador.gestorTS.getTipo(idPos));
            } else {
                atrV.setTipo(Procesador.gestorTS.getTipo(idPos) + " " + atrW.getTipo());
            }

            //ASem: V.long:= 1 + W.long 
            atrV.setLong(1 + atrW.getLong());

        }
        debug(atrV);
        return atrV;
    }

    //Funcion Correspondiente al simbolo no terminal: W
    //Acciones posibles:
    //      	W → , id W1
    //          W → λ	
    private static Atributos W() {

        Atributos atrID;
        Atributos atrW = new Atributos();
        Atributos atrW1;
        if (tokenActualCoincideCualquiera("COMA")) {

            printParse("54");   //54.	W → , id W1
            //ASin: ,
            match("COMA");
            //ASin: id
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: W
            atrW1 = W();
            //ASem: W.tipo:=	if (W1.tipo = vacío)
            //      				then buscaTipoTS (id.pos)
            //      			else buscaTipoTS (id.pos) x W1.tipo
            if (atrW1.getTipo().equals("vacío")) {
                atrW.setTipo(Procesador.gestorTS.getTipo(idPos));
            } else {
                atrW.setTipo(Procesador.gestorTS.getTipo(idPos) + " " + atrW.getTipo());
            }
            //ASem: W.long:= 1 + W1.long 
            atrW.setLong(atrW1.getLong() + 1);

        } else if (tokenActualCoincideCualquiera("PARENT_CERRAR")) {

            printParse("55");   //55.	W → λ	

            //ASem: W.tipo:= vacío
            atrW.setTipo("vacío");
            //ASem: W.long:= 0
            atrW.setLong(0);

        }

        debug(atrW);
        return atrW;
    }

    //Funcion Correspondiente al simbolo no terminal: Y
    //Acciones posibles:
    //      	Y → E
    //          Y → λ
    private static Atributos Y() {

        Atributos atrY = new Atributos();
        Atributos atrE;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("56");//56.	Y → E
            //ASin: E
            atrE = E();
            //ASem: Y.tipo = E.tipo
            atrY.setTipo(atrE.getTipo());

        } else if (tokenActualCoincideCualquiera("PYC")) {

            printParse("57"); //57.	Y → λ
            //Asem: Y.tipo = vacío
            atrY.setTipo("vacío");
        }

        debug(atrY);
        return atrY;
    }

    //Funcion Correspondiente al simbolo no terminal: E
    //Acciones posibles:
    //      	E → F Eprima
    private static Atributos E() {

        Atributos atrE = new Atributos();
        Atributos atrF;
        Atributos atrEprima;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("58");   //58.	E → F Eprima
            //ASin: F
            atrF = F();
            //ASin: Eprima
            atrEprima = Eprima();
            //ASem: 
            // if (Eprima.tipo = vacío)  
            //      E.tipo:= F.tipo  
            //  else if (F.tipo = Eprima.tipo = lógico) 
            //      E.tipo:= lógico 
            //  else  
            //      E.tipo:= tipo_error 
            if (atrEprima.getTipo().equals("vacío")) {
                atrE.setTipo(atrF.getTipo());

            } else if (atrF.getTipo().equals("lógico") && atrEprima.getTipo().equals("lógico")) {
                atrE.setTipo("lógico");
            } else {
                atrE.setTipo("tipo_error");
            }

            if (atrE.getTipo().equals("tipo_error")) {
                GestorError.writeError("semántico", "Error dentro de la expresión");
            }

        }

        debug(atrE);
        return atrE;
    }

    //Funcion Correspondiente al simbolo no terminal: Eprima
    //Acciones posibles:
    //      	Eprima → or F Eprima
    //          Eprima → xor F Eprima
    //          Eprima → λ
    private static Atributos Eprima() {

        Atributos atrEprima = new Atributos();
        Atributos atrF;
        Atributos atrEprima1;

        if (tokenActualCoincideCualquiera("OR")) {

            printParse("59");   //59.	Eprima → or F Eprima1
            //ASin: or
            match("OR");
            //ASin: F
            atrF = F();
            //ASin: Eprima
            atrEprima1 = Eprima();
            //ASem:
            // if (Eprima1.tipo = vacío){ 
            //     if(F.tipo = lógico) 
            //          Eprima.tipo:= lógico 
            //     else  
            //          Eprima.tipo:= tipo_error  
            // } else if (F.tipo = Eprima1.tipo = lógico){ 
            //     E.prima.tipo:= lógico 
            // }else { 
            //     Eprima.tipo:= tipo_error 
            //   }

            if (atrEprima1.getTipo().equals("vacío")) {
                if (atrF.getTipo().equals("lógico")) {
                    atrEprima.setTipo("lógico");
                } else {
                    atrEprima.setTipo("tipo_error");
                }
            } else if (atrF.getTipo().equals("lógico") && atrEprima1.getTipo().equals("lógico")) {
                atrEprima.setTipo("lógico");
            } else {
                atrEprima.setTipo("tipo_error");

            }

        } else if (tokenActualCoincideCualquiera("XOR")) {

            printParse("60");   //60.	Eprima → xor F Eprima
            //ASin: xor
            match("XOR");
            //ASin: F
            atrF = F();
            //ASin: Eprima
            atrEprima1 = Eprima();
            //ASem:
            // if (Eprima1.tipo = vacío){ 
            //     if(F.tipo = lógico) 
            //          Eprima.tipo:= lógico 
            //     else  
            //          Eprima.tipo:= tipo_error  
            // } else if (F.tipo = Eprima1.tipo = lógico){ 
            //     E.prima.tipo:= lógico 
            // }else { 
            //     Eprima.tipo:= tipo_error 
            //   }

            if (atrEprima1.getTipo().equals("vacío")) {
                if (atrF.getTipo().equals("lógico")) {
                    atrEprima.setTipo("lógico");
                } else {
                    atrEprima.setTipo("tipo_error");
                }
            } else if (atrF.getTipo().equals("lógico") && atrEprima1.getTipo().equals("lógico")) {
                atrEprima.setTipo("lógico");
            } else {
                atrEprima.setTipo("tipo_error");

            }

        } else if (tokenActualCoincideCualquiera("PARENT_CERRAR", "COMA", "PYC", "DO", "OF", "THEN", "TO")) {

            printParse("61");   //61.	Eprima → λ
            //ASem: Eprima.tipo = vacío
            atrEprima.setTipo("vacío");
        }

        debug(atrEprima);
        return atrEprima;
    }

    //Funcion Correspondiente al simbolo no terminal: F
    //Acciones posibles:
    //      	F → G Fprima
    private static Atributos F() {

        Atributos atrF = new Atributos();
        Atributos atrG;
        Atributos atrFprima;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("62");//62.	F → G Fprima
            //ASin: G
            atrG = G();
            //ASin: Fprima
            atrFprima = Fprima();
            //ASem: 
            //if (Fprima.tipo = vacío) 
            //   then F.tipo:= G.tipo 
            //else if (G.tipo = lógico AND Fprima.tipo = lógico) 
            //   then F.tipo:= lógico 
            //else  
            //   F.tipo:= tipo_error
            if (atrFprima.getTipo().equals("vacío")) {
                atrF.setTipo(atrG.getTipo());
            } else if (atrG.getTipo().equals("lógico") && atrFprima.getTipo().equals("lógico")) {
                atrF.setTipo("lógico");
            } else {
                atrF.setTipo("tipo_error");
            }
        }
        debug(atrF);
        return atrF;
    }

    //Funcion Correspondiente al simbolo no terminal: Fprima
    //Acciones posibles:
    //      	Fprima → and G Fprima
    //          Fprima → λ
    private static Atributos Fprima() {

        Atributos atrFprima = new Atributos();
        Atributos atrG;
        Atributos atrFprima1;
        if (tokenActualCoincideCualquiera("AND")) {

            printParse("63");   //63.	Fprima → and G Fprima
            //ASin: and
            match("AND");
            //ASin: G
            atrG = G();
            //ASin: Fprima
            atrFprima1 = Fprima();
            //ASem:
            // if (Fprima1.tipo = vacío) {
            //     if (G.tipo = lógico){
            //          Fprima.tipo:= lógico
            //     } else {
            //          Fprima.tipo:= tipo_error
            //     }
            //  } else if (G.tipo = Fprima1.tipo = lógico){
            //     Fprima.tipo:= lógico
            //  } else {
            //     Fprima.tipo:= tipo_error
            //   } 

            if (atrFprima1.getTipo().equals("vacío")) {
                if (atrG.getTipo().equals("lógico")) {
                    atrFprima.setTipo("lógico");
                } else {
                    atrFprima.setTipo("tipo_error");
                }
            } else if (atrFprima1.getTipo().equals("vacío") && atrG.getTipo().equals("lógico")) {
                atrFprima.setTipo("lógico");
            } else {
                atrFprima.setTipo("tipo_error");
            }
        } else if (tokenActualCoincideCualquiera("PARENT_CERRAR", "COMA", "PYC", "DO", "OF", "OR", "THEN", "TO", "XOR")) {

            printParse("64");   //64.	Fprima → λ
            //ASem: Fprima.tipo = vacío
            atrFprima.setTipo("vacío");
        }

        debug(atrFprima);
        return atrFprima;
    }

    //Funcion Correspondiente al simbolo no terminal: G
    //Acciones posibles:
    //      	G → H Gprima
    private static Atributos G() {

        Atributos atrG = new Atributos();
        Atributos atrH;
        Atributos atrGprima;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("65");   //65.	G → H Gprima

            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima = Gprima();
            //ASem:	
            // if (Gprima.tipo = vacío) { 
            //       G.tipo:= H.tipo  
            //  } else if (H.tipo = entero AND Gprima.tipo = lógico){ 
            //     G.tipo:= lógico 
            //  } else { 
            //     G.tipo:= tipo_error 
            //  } 
            if (atrGprima.getTipo().equals("vacío")) {
                atrG.setTipo(atrH.getTipo());
            } else if (atrH.getTipo().equals("entero") && atrGprima.getTipo().equals("lógico")) {
                atrG.setTipo("lógico");
            } else {
                atrG.setTipo("tipo_error");
            }

        }
        debug(atrG);
        return atrG;

    }

    //Funcion Correspondiente al simbolo no terminal: Gprima
    //Acciones posibles:
    //      	Gprima → = H Gprima
    //          Gprima → <> H Gprima
    //          Gprima → > H Gprima
    //          Gprima → >= H Gprima
    //          Gprima → < H Gprima
    //          Gprima → <= H Gprima
    //          Gprima → λ
    private static Atributos Gprima() {

        Atributos atrGprima = new Atributos();
        Atributos atrH;
        Atributos atrGprima1;
        if (tokenActualCoincideCualquiera("IGUAL")) {

            printParse("66");//66.	Gprima → = H Gprima
            //ASin: =
            match("IGUAL");
            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima1 = Gprima();
            //ASem: 
            // if (Gprima1.tipo = vacío) { 
            //     if (H.tipo = entero){ 
            //          Gprima.tipo:= lógico 
            //     } else { 
            //          Gprima.tipo:= tipo_error  
            //     } 
            //    else { 
            //     Gprima.tipo:= tipo_error  
            //   } 

            if (atrGprima1.getTipo().equals("vacío")) {
                if (atrH.getTipo().equals("entero")) {
                    atrGprima.setTipo("lógico");
                } else {
                    atrGprima.setTipo("tipo_error");
                }
            } else if (atrGprima1.getTipo().equals("lógico") && atrH.getTipo().equals("entero")) {
                atrGprima.setTipo("lógico");
            } else {
                atrGprima.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("DISTINTO")) {

            printParse("67");//67.	Gprima → <> H Gprima
            //ASin: <>
            match("DISTINTO");
            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima1 = Gprima();
            //ASem: 
            // if (Gprima1.tipo = vacío) { 
            //     if (H.tipo = entero){ 
            //          Gprima.tipo:= lógico 
            //     } else { 
            //          Gprima.tipo:= tipo_error  
            //     } 
            // else { 
            //     Gprima.tipo:= tipo_error  
            //   } 

            if (atrGprima1.getTipo().equals("vacío")) {
                if (atrH.getTipo().equals("entero")) {
                    atrGprima.setTipo("lógico");
                } else {
                    atrGprima.setTipo("tipo_error");
                }
            } else if (atrGprima1.getTipo().equals("lógico") && atrH.getTipo().equals("entero")) {
                atrGprima.setTipo("lógico");
            } else {
                atrGprima.setTipo("tipo_error");
            }
        } else if (tokenActualCoincideCualquiera("MAYOR")) {

            printParse("68");   //68.	Gprima → > H Gprima
            //ASin: >
            match("MAYOR");
            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima1 = Gprima();
            //ASem: 
            // if (Gprima1.tipo = vacío) { 
            //     if (H.tipo = entero){ 
            //          Gprima.tipo:= lógico 
            //     } else { 
            //          Gprima.tipo:= tipo_error  
            //     } 
            // else { 
            //     Gprima.tipo:= tipo_error  
            //   } 

            if (atrGprima1.getTipo().equals("vacío")) {
                if (atrH.getTipo().equals("entero")) {
                    atrGprima.setTipo("lógico");
                } else {
                    atrGprima.setTipo("tipo_error");
                }
            } else if (atrGprima1.getTipo().equals("lógico") && atrH.getTipo().equals("entero")) {
                atrGprima.setTipo("lógico");
            } else {
                atrGprima.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MAYOR_IGUAL")) {

            printParse("69");//69.	Gprima → >= H Gprima
            //ASin: >=
            match("MAYOR_IGUAL");
            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima1 = Gprima();
            //ASem: 
            // if (Gprima1.tipo = vacío) { 
            //     if (H.tipo = entero){ 
            //          Gprima.tipo:= lógico 
            //     } else { 
            //          Gprima.tipo:= tipo_error  
            //     } 
            // else { 
            //     Gprima.tipo:= tipo_error  
            //   } 

            if (atrGprima1.getTipo().equals("vacío")) {
                if (atrH.getTipo().equals("entero")) {
                    atrGprima.setTipo("lógico");
                } else {
                    atrGprima.setTipo("tipo_error");
                }
            } else if (atrGprima1.getTipo().equals("lógico") && atrH.getTipo().equals("entero")) {
                atrGprima.setTipo("lógico");
            } else {
                atrGprima.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MENOR")) {

            printParse("70");//70.	Gprima → < H Gprima
            //ASin: <
            match("MENOR");
            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima1 = Gprima();
            //ASem: 
            // if (Gprima1.tipo = vacío) { 
            //     if (H.tipo = entero){ 
            //          Gprima.tipo:= lógico 
            //     } else { 
            //          Gprima.tipo:= tipo_error  
            //     } 
            // else { 
            //     Gprima.tipo:= tipo_error  
            //   } 

            if (atrGprima1.getTipo().equals("vacío")) {
                if (atrH.getTipo().equals("entero")) {
                    atrGprima.setTipo("lógico");
                } else {
                    atrGprima.setTipo("tipo_error");
                }
            } else if (atrGprima1.getTipo().equals("lógico") && atrH.getTipo().equals("entero")) {
                atrGprima.setTipo("lógico");
            } else {
                atrGprima.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MENOR_IGUAL")) {

            printParse("71");//71.	Gprima → <= H Gprima
            //ASin: <=
            match("MENOR_IGUAL");
            //ASin: H
            atrH = H();
            //ASin: Gprima
            atrGprima1 = Gprima();
            //ASem: 
            // if (Gprima1.tipo = vacío) { 
            //     if (H.tipo = entero){ 
            //          Gprima.tipo:= lógico 
            //     } else { 
            //          Gprima.tipo:= tipo_error  
            //     } 
            // else { 
            //     Gprima.tipo:= tipo_error  
            //   } 

            if (atrGprima1.getTipo().equals("vacío")) {
                if (atrH.getTipo().equals("entero")) {
                    atrGprima.setTipo("lógico");
                } else {
                    atrGprima.setTipo("tipo_error");
                }
            } else if (atrGprima1.getTipo().equals("lógico") && atrH.getTipo().equals("entero")) {
                atrGprima.setTipo("lógico");
            } else {
                atrGprima.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera(
                "PARENT_CERRAR", "COMA", "PYC",
                "AND", "DO", "OF", "OR", "THEN", "TO", "XOR")) {

            printParse("72");   //72.	Gprima → λ
            //ASem:Gprima.tipo = vacío
            atrGprima.setTipo("vacío");
        }

        debug(atrGprima);
        return atrGprima;
    }

    //Funcion Correspondiente al simbolo no terminal: H
    //Acciones posibles:
    //      	H → I Hprima
    private static Atributos H() {

        Atributos atrH = new Atributos();
        Atributos atrI;
        Atributos atrHprima;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("73");//73.	H → I Hprima
            //ASin: I
            atrI = I();
            //ASin: Hprima
            atrHprima = Hprima();
            //ASem:
            // if (Hprima.tipo = vacío) { 
            //     H.tipo:= I.tipo  
            // }else if (I.tipo = Hprima.tipo = cadena) { 
            //     H.tipo:= cadena 
            // }else if (I.tipo = entero AND Hprima.tipo = entero){ 
            //     H.tipo:= entero 
            // }else {  
            //     H.tipo:= tipo_error 
            // } 

            if (atrHprima.getTipo().equals("vacío")) {
                atrH.setTipo(atrI.getTipo());
            } else if (atrI.getTipo().equals("cadena") && atrHprima.getTipo().equals("cadena")) {
                atrH.setTipo("cadena");
            } else if (atrI.getTipo().equals("entero") && atrHprima.getTipo().equals("entero")) {
                atrH.setTipo("entero");
            } else {
                atrH.setTipo("tipo_error");
            }
        }
        debug(atrH);
        return atrH;

    }

    //Funcion Correspondiente al simbolo no terminal: Hprima
    //Acciones posibles:
    //      	Hprima → + I Hprima
    //          Hprima → - I Hprima
    //          Hprima → λ
    private static Atributos Hprima() {

        Atributos atrHprima = new Atributos();
        Atributos atrI;
        Atributos atrHprima1;

        if (tokenActualCoincideCualquiera("MAS")) {

            printParse("74");//74.	Hprima → + I Hprima
            //ASin: +
            match("MAS");
            //ASin: I
            atrI = I();
            //ASin: Hprima
            atrHprima1 = Hprima();
            //ASem:
            // if (Hprima1.tipo = vacío){ 
            //     if (I.tipo  {entero, cadena}){ 
            //          Hprima.tipo:= I.tipo 
            //     }else { 
            //          Hprima.tipo:= tipo_error 
            //     } 
            // } else if (I.tipo = Hprima1.tipo  {entero, cadena}) { 
            //     Hprima.tipo:= I.tipo 
            // }else { 
            //     Hprima.tipo:= tipo_error 
            //   } 

            if (atrHprima1.getTipo().equals("vacío")) {
                if (atrI.getTipo().equals("cadena") || atrI.getTipo().equals("entero")) {
                    atrHprima.setTipo(atrI.getTipo());
                } else {
                    atrHprima.setTipo("tipo_error");
                }
            } else if (atrHprima1.getTipo().equals(atrI.getTipo()) && (atrI.getTipo().equals("entero") || atrI.getTipo().equals("cadena"))) {
                atrHprima.setTipo(atrI.getTipo());
            } else {
                atrHprima.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MENOS")) {

            printParse("75");//75.	Hprima → - I Hprima
            //ASin: -
            match("MENOS");
            //ASin: I
            atrI = I();
            //ASin: Hprima
            atrHprima1 = Hprima();
            //ASem:
            // if (Hprima1.tipo = vacío){ 
            //     if (I.tipo  {entero, cadena}){ 
            //          Hprima.tipo:= I.tipo 
            //     }else { 
            //          Hprima.tipo:= tipo_error 
            //     } 
            // } else if (I.tipo = Hprima1.tipo  {entero, cadena}) { 
            //     Hprima.tipo:= I.tipo 
            // }else { 
            //     Hprima.tipo:= tipo_error 
            //   } 

            if (atrHprima1.getTipo().equals("vacío")) {
                if (atrI.getTipo().equals("entero")) {
                    atrHprima.setTipo("entero");
                } else {
                    atrHprima.setTipo("tipo_error");
                }
            } else if (atrHprima1.getTipo().equals("entero") && atrI.getTipo().equals("entero")) {
                atrHprima.setTipo("entero");
            } else {
                atrHprima.setTipo("tipo_error");
            }
        } else if (tokenActualCoincideCualquiera(
                "PARENT_CERRAR", "COMA", "PYC", "MAYOR",
                "MAYOR_IGUAL", "MENOR", "MENOR_IGUAL", "IGUAL",
                "DISTINTO", "AND", "DO", "OF", "OR", "THEN", "TO", "XOR")) {

            printParse("76");   //76.	Hprima → λ
            //Asem:Hprima.tipo = vacío
            atrHprima.setTipo("vacío");
        }

        debug(atrHprima);
        return atrHprima;
    }

    //Funcion Correspondiente al simbolo no terminal: I
    //Acciones posibles:
    //      	I → J Iprima
    private static Atributos I() {

        Atributos atrI = new Atributos();
        Atributos atrJ;
        Atributos atrIprima;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("77");   //77.	I → J Iprima
            //ASin: J
            atrJ = J();
            //ASin: Iprima
            atrIprima = Iprima();
            //ASem:	
            // if (Iprima.tipo = vacío) { 
            //     I.tipo:= J.tipo  
            // } else if (J.tipo = Iprima.tipo = entero) { 
            //     I.tipo:= entero 
            // } else { 
            //     J.tipo:= tipo_error 
            //   }

            if (atrIprima.getTipo().equals("vacío")) {
                atrI.setTipo(atrJ.getTipo());
            } else if (atrJ.getTipo().equals("entero") && atrIprima.getTipo().equals("entero")) {
                atrI.setTipo("entero");
            } else {
                atrI.setTipo("tipo_error");
            }
        }
        debug(atrI);
        return atrI;

    }

    //Funcion Correspondiente al simbolo no terminal: Iprima
    //Acciones posibles:
    //      	Iprima → * J Iprima
    //          Iprima → / J Iprima
    //          Iprima → mod J Iprima
    private static Atributos Iprima() {

        Atributos atrIprima = new Atributos();
        Atributos atrJ;
        Atributos atrIprima1;
        if (tokenActualCoincideCualquiera("PRODUCTO")) {

            printParse("78"); //78.	Iprima → * J Iprima
            //ASin: *
            match("PRODUCTO");
            //ASin: J
            atrJ = J();
            //ASin: Iprima
            atrIprima1 = Iprima();
            //Asem:  
            // if (Iprima1.tipo = vacío) { 
            //     if (J.tipo = entero) { 
            //          Iprima.tipo:= entero 
            //     }else {  
            //          Iprima.tipo:= tipo_error 
            //     } 
            // }else if (J.tipo = Iprima1.tipo = entero){ 
            //     Iprima.tipo:= entero 
            // }else { 
            //     Iprima.tipo:= tipo_error  
            //  } 

            if (atrIprima1.getTipo().equals("vacío")) {
                if (atrJ.getTipo().equals("entero")) {
                    atrIprima.setTipo("entero");
                } else {
                    atrIprima.setTipo("tipo_error");
                }
            } else if (atrIprima1.getTipo().equals("entero") && atrJ.getTipo().equals("entero")) {
                atrIprima.setTipo("entero");
            } else {
                atrIprima.setTipo("vacío");
            }
        } else if (tokenActualCoincideCualquiera("DIVISION")) {

            printParse("79"); //79.	Iprima → / J Iprima
            //ASin: /
            match("DIVISION");
            //ASin: J
            atrJ = J();
            //ASin: Iprima
            atrIprima1 = Iprima();
            //Asem:  
            // if (Iprima1.tipo = vacío) { 
            //     if (J.tipo = entero) { 
            //          Iprima.tipo:= entero 
            //     }else {  
            //          Iprima.tipo:= tipo_error 
            //     } 
            // }else if (J.tipo = Iprima1.tipo = entero){ 
            //     Iprima.tipo:= entero 
            // }else { 
            //     Iprima.tipo:= tipo_error  
            //  } 

            if (atrIprima1.getTipo().equals("vacío")) {
                if (atrJ.getTipo().equals("entero")) {
                    atrIprima.setTipo("entero");
                } else {
                    atrIprima.setTipo("tipo_error");
                }
            } else if (atrIprima1.getTipo().equals("entero") && atrJ.getTipo().equals("entero")) {
                atrIprima.setTipo("entero");
            } else {
                atrIprima.setTipo("vacío");
            }

        } else if (tokenActualCoincideCualquiera("MOD")) {

            printParse("80");//80.	Iprima → mod J Iprima
            //ASin: mod
            match("MOD");
            //ASin: J
            atrJ = J();
            //ASin: Iprima
            atrIprima1 = Iprima();
            //Asem:  
            // if (Iprima1.tipo = vacío) { 
            //     if (J.tipo = entero) { 
            //          Iprima.tipo:= entero 
            //     }else {  
            //          Iprima.tipo:= tipo_error 
            //     } 
            // }else if (J.tipo = Iprima1.tipo = entero){ 
            //     Iprima.tipo:= entero 
            // }else { 
            //     Iprima.tipo:= tipo_error  
            //  } 

            if (atrIprima1.getTipo().equals("vacío")) {
                if (atrJ.getTipo().equals("entero")) {
                    atrIprima.setTipo("entero");
                } else {
                    atrIprima.setTipo("tipo_error");
                }
            } else if (atrIprima1.getTipo().equals("entero") && atrJ.getTipo().equals("entero")) {
                atrIprima.setTipo("entero");
            } else {
                atrIprima.setTipo("vacío");
            }

        } else if (tokenActualCoincideCualquiera(
                "PARENT_CERRAR", "MAS", "COMA", "MENOS",
                "PYC", "MAYOR", "MAYOR_IGUAL", "MENOR", "MENOR_IGUAL", "IGUAL",
                "DISTINTO", "AND", "DO", "OF", "OR", "THEN", "TO", "XOR")) {

            printParse("81");   //81.	Iprima → λ
            //Asem:Iprima.tipo = vacío
            atrIprima.setTipo("vacío");
        }

        debug(atrIprima);
        return atrIprima;
    }

    //Funcion Correspondiente al simbolo no terminal: J
    //Acciones posibles:
    //      	J → K Jprima
    private static Atributos J() {

        Atributos atrJ = new Atributos();
        Atributos atrK;
        Atributos atrJprima;
        if (tokenActualCoincideCualquiera("PARENT_ABRIR", "MAS", "MENOS", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "NOT", "TRUE")) {

            printParse("82");   //82.	J → K Jprima
            //ASin: K
            atrK = K();
            //ASin: Jprima
            atrJprima = Jprima();
            //ASem:	
            // if (Jprima.tipo = vacío) { 
            //     J.tipo:= K.tipo  
            // }else if (K.tipo = Jprima.tipo = entero){ 
            //     J.tipo:= entero 
            // }else { 
            //     J.tipo:= tipo_error 
            // } 
            if (atrJprima.getTipo().equals("vacío")) {
                atrJ.setTipo(atrK.getTipo());
            } else if (atrK.getTipo().equals("entero") && atrJprima.getTipo().equals("entero")) {
                atrJ.setTipo("entero");
            } else {
                atrJ.setTipo("tipo_error");
            }
        }
        debug(atrJ);
        return atrJ;

    }

    //Funcion Correspondiente al simbolo no terminal: Jprima
    //Acciones posibles:
    //      	Jprima → ^ K Jprima
    //          Jprima → λ
    private static Atributos Jprima() {

        Atributos atrJprima = new Atributos();
        Atributos atrK;
        Atributos atrJprima1;
        if (tokenActualCoincideCualquiera("POTENCIA")) {

            printParse("83");   //83.	Jprima → ** K Jprima
            //ASin: **
            match("POTENCIA");
            //ASin: K
            atrK = K();
            //ASin: Jprima
            atrJprima1 = Jprima();
            //Asem: 
            // if (Jprima1.tipo = vacío){ 
            //     if (K.tipo = entero){ 
            //          Jprima.tipo:= entero 
            //     } else{ 
            //          Jprima.tipo:= tipo_error 
            //     } 
            // } else if (K.tipo = Jprima1.tipo = entero){ 
            //     Jprima.tipo:= entero 
            // }else{ 
            //     Jprima.tipo:= tipo_error 
            // } 

            if (atrJprima1.getTipo().equals("entero") || atrJprima1.getTipo().equals("vacío")) {
                if (atrK.getTipo().equals("entero")) {
                    atrJprima.setTipo("entero");
                } else {
                    atrJprima.setTipo("tipo_error");
                }
            } else {
                atrJprima.setTipo("tipo_error");
            }
        } else if (tokenActualCoincideCualquiera(
                "PARENT_CERRAR", "PRODUCTO", "MAS", "COMA", "MENOS",
                "DIVISION", "PYC", "MAYOR", "MAYOR_IGUAL", "MENOR", "MENOR_IGUAL", "IGUAL",
                "DISTINTO", "AND", "DO", "MOD", "OF", "OR", "THEN", "TO", "XOR")) {

            printParse("84");   //84.	Jprima → λ
            //Asem:Jprima.tipo = vacío
            atrJprima.setTipo("vacío");
        }

        debug(atrJprima);
        return atrJprima;
    }

    //Funcion Correspondiente al simbolo no terminal: K
    //Acciones posibles:
    //      	K → not K
    //          K → + K
    //          K → - K
    //          K → Z
    private static Atributos K() {

        Atributos atrK = new Atributos();
        Atributos atrK1;
        Atributos atrZ;
        if (tokenActualCoincideCualquiera("NOT")) {

            printParse("85");   //85.	K → not K
            //ASin: not
            match("NOT");
            //ASin: K
            atrK1 = K();
            //ASem: 
            // if (K1.tipo = lógico){ 
            //     K.tipo:= lógico 
            // }else{  
            //     K.tipo:= tipo_error 
            //   } 
            if (atrK1.getTipo().equals("lógico")) {
                atrK.setTipo("lógico");
            } else {
                atrK.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MAS")) {

            printParse("86");   //86.	K → + K
            //ASin: +
            match("MAS");
            //ASin: K
            atrK1 = K();
            //ASem: 
            // if (K1.tipo = entero){ 
            //     K.tipo:= entero 
            // }else{  
            //     K.tipo:= tipo_error 
            //   } 
            if (atrK1.getTipo().equals("entero")) {
                atrK.setTipo("entero");
            } else {
                atrK.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MENOS")) {

            printParse("87");   //87.	K → - K
            //ASin: -
            match("MENOS");
            atrK1 = K();
            //ASem: 
            // if (K1.tipo = entero){ 
            //     K.tipo:= entero 
            // }else{  
            //     K.tipo:= tipo_error 
            //   } 
            if (atrK1.getTipo().equals("entero")) {
                atrK.setTipo("entero");
            } else {
                atrK.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("PARENT_ABRIR", "CADENA", "ENTERO", "FALSE", "ID", "MAX", "MIN", "TRUE")) {

            printParse("88");   //88.	K → Z

            atrZ = Z();
            //ASem: K.tipo = Z.tipo
            atrK.setTipo(atrZ.getTipo());
        }

        debug(atrK);
        return atrK;
    }

    //Funcion Correspondiente al simbolo no terminal: Z
    //Acciones posibles:
    //      	Z → ENTERO Zprima
    //          Z → CADENA
    //          Z → TRUE 
    //          Z → FALSE
    //          Z → ID LL Zprima
    //          Z → ( E ) Zprima
    //          Z → MAX ( L )
    //          Z → MIN ( L )
    private static Atributos Z() {

        Atributos atrZ = new Atributos();
        Atributos atrZprima;
        Atributos atrID;
        Atributos atrL;
        Atributos atrLL;
        Atributos atrE;
        if (tokenActualCoincideCualquiera("ENTERO")) {

            printParse("89");   //89.	Z → ENTERO Zprima

            match("ENTERO");
            atrZprima = Zprima();
            //ASem: 
            // if (Zprima.tipo = vacío){ 
            //     Z.tipo:= entero 
            // }else if (Zprima.tipo = entero){ 
            //     Z.tipo:= lógico 
            // }else { 
            //     Z.tipo:= tipo_error} 
            // } 
            if (atrZprima.getTipo().equals("vacío")) {
                atrZ.setTipo("entero");
            } else if (atrZprima.getTipo().equals("entero")) {
                atrZ.setTipo("lógico");
            } else {
                atrZ.setTipo("tipo_error");
            }
        } else if (tokenActualCoincideCualquiera("CADENA")) {

            printParse("90");   //90.	Z → CADENA
            //ASin: CADENA
            match("CADENA");
            //ASem: Z.tipo:= cadena 
            atrZ.setTipo("cadena");

        } else if (tokenActualCoincideCualquiera("TRUE")) {

            printParse("91");   //91.	Z → TRUE
            //ASin: TRUE
            match("TRUE");
            //ASem: Z.tipo:= lógico
            atrZ.setTipo("lógico");

        } else if (tokenActualCoincideCualquiera("FALSE")) {

            printParse("92");   //92.	Z → FALSE
            //ASin: FALSE
            match("FALSE");
            //ASem: Z.tipo:= lógico
            atrZ.setTipo("lógico");

        } else if (tokenActualCoincideCualquiera("ID")) {
            printParse("93");//93.	Z → ID LL Zprima
            //ASin: ID
            atrID = match("ID");
            int idPos = atrID.getPos();
            //ASin: LL
            atrLL = LL();
            //ASin: Zprima
            atrZprima = Zprima();
            //id.tipo = BuscaTipoTS(id.pos)
            atrID.setTipo(Procesador.gestorTS.getTipo(idPos));

            //ASem:
            // if (id.tipo = LL.tipo→t){ 
            //     if (t ≠ vacío){ // id es función 
            //          if (Zprima.tipo = vacío){ 
            //              Z.tipo:= t 
            //          }else if (Zprima.tipo = t = entero){ 
            //              Z.tipo:= lógico 
            //          }else{ 
            //              Z.tipo:= tipo_error
            //          }
            //     }else{ 
            //          Z.tipo:= tipo_error // la función tendría que devolver un valor
            //     }
            // } else if (LL.tipo = vacío){ // id es variable
            //     if (Zprima.tipo = vacío)
            //          Z.tipo:= id.tipo
            //     }else if (Zprima.tipo = id.tipo = entero){
            //          Z.tipo:= lógico
            //     }else{
            //          Z.tipo:= tipo_error
            //     }
            // } else{
            //      Z.tipo:= tipo_error // variable con parámetros 
            // }
            /* Para esta accion semantica se ha realizado primero la comprobación de el ID, y despues se realiza la comprobacion de Zprima,
                por ello, el siguiente codigo difiere de la Accion Semantica anterior. */


            if (atrID.getTipo().equals("procedimiento") && Procesador.gestorTS.getValorAtributoCad(idPos, "etiqueta").equals("main")) {        //llamada a programa/main
                GestorError.writeError("semántico", "LLamada ilegal al programa principal");
                atrZ.setTipo("tipo_error");
            } else if (atrID.getTipo().equals("procedure")) {       //llamada a procedure
                GestorError.writeError("semántico", "No se puede realizar una llamada a un PROCEDURE aquí");
                atrZ.setTipo("tipo_error");
            } else if (atrID.getTipo().equals("función")) {     //llamada a funcion
                String tipoRet = Procesador.gestorTS.getValorAtributoCad(idPos, "tipoRetorno");
                int numParam = Procesador.gestorTS.getValorAtributoEnt(idPos, "numParametro");

                if (numParam > 0) {
                    String[] llAtributos = atrLL.getTipo().split(" ");
                    String[] idAtbAtributos = Procesador.gestorTS.getValorAtributoLista(idPos, "tipoParametros");
                    if (llAtributos.length == idAtbAtributos.length && Arrays.compare(llAtributos, idAtbAtributos) == 0) {
                        atrZ.setTipo(tipoRet);
                    } else {
                        GestorError.writeError("semántico", "Los parámetros de la función no coinciden.\n \t Se ha recibido: "
                                + Arrays.toString(llAtributos) + "\n \t pero se deberia recibir: "
                                + Arrays.toString(idAtbAtributos));
                        atrZ.setTipo("tipo_error");
                    }
                } else if (numParam == 0 && atrLL.getLong() != 0) {
                    String[] sColaAtributos = atrLL.getTipo().split(" ");

                    GestorError.writeError("semántico", "Los parámetros de la función no coinciden.\n \t Se ha recibido: "
                            + Arrays.toString(sColaAtributos) + "\n \t pero no tiene ningún parámetro");
                    atrZ.setTipo("tipo_error");
                } else {
                    atrZ.setTipo(tipoRet);
                }
            } else if (atrID.getTipo().equals("entero") || atrID.getTipo().equals("cadena") || atrID.getTipo().equals("lógico")) {
                atrZ.setTipo(atrID.getTipo());
            }

            //comprobacion de Zprima
            if (atrZprima.getTipo().equals("vacío")) {
                //atrZ.tipo se queda igual, por que ya se ha asignado antes
            } else if (atrZprima.getTipo().equals("entero") && atrZ.getTipo().equals("entero")) {
                atrZ.setTipo("lógico");
            } else {
                atrZ.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("PARENT_ABRIR")) {

            printParse("94");   //94.     Z → ( E ) Zprima

            //ASin: (
            match("PARENT_ABRIR");
            //ASin: E
            atrE = E();
            //ASin: )
            match("PARENT_CERRAR");
            //ASin: Zprima
            atrZprima = Zprima();
            //ASem:
            // if (Zprima.tipo = vacío) { 
            //     Z.tipo:= E.tipo 
            // } else if (Zprima.tipo = E.tipo = entero){ 
            //     Z.tipo:= lógico 
            // }else{ 
            //     Z.tipo:= tipo_error 
            //   } 
            if (atrZprima.getTipo().equals("vacío")) {
                atrZ.setTipo(atrE.getTipo());
            } else if (atrZprima.getTipo().equals("entero") && atrE.getTipo().equals("entero")) {
                atrZ.setTipo("lógico");
            } else {
                atrZ.setTipo("tipo_error");
            }

        } else if (tokenActualCoincideCualquiera("MAX")) {

            printParse("95");//95.	Z → MAX ( L )
            //ASin: MAX
            match("MAX");
            //ASin: (
            match("PARENT_ABRIR");
            //ASin: L
            atrL = L();
            //ASin: )
            match("PARENT_CERRAR");
            //ASem: Z.tipo:= entero
            atrZ.setTipo("entero");
            //ASem: for i:= 1 to L.long
            //  		if (L.tipo[i] ≠ entero)
            //  			then Z.tipo := tipo_error
            if (!atrL.getTipo().equals("vacío")) {
                String[] lista = atrL.getTipo().split(" ");
                for (String item : lista) {
                    if (!item.equals("entero")) {
                        atrZ.setTipo("tipo_error");
                    }
                }
            }

        } else if (tokenActualCoincideCualquiera("MIN")) {

            printParse("96");//96.	Z → MIN ( L )
            //ASin: MIN
            match("MIN");
            //ASin: (
            match("PARENT_ABRIR");
            //ASin: L
            atrL = L();
            //ASin: )
            match("PARENT_CERRAR");
            //ASem: Z.tipo:= entero
            atrZ.setTipo("entero");
            //ASem: for i:= 1 to L.long
            //  		if (L.tipo[i] ≠ entero)
            //  			then Z.tipo:= tipo_error
            if (!atrL.getTipo().equals("vacío")) {
                String[] lista = atrL.getTipo().split(" ");
                for (String item : lista) {
                    if (!item.equals("entero")) {
                        atrZ.setTipo("tipo_error");
                    }
                }
            }
        }
        debug(atrZ);
        return atrZ;
    }

    //Funcion Correspondiente al simbolo no terminal: Zprima
    //Acciones posibles:
    //      	Zprima → in ( L )
    //          Zprima → λ
    private static Atributos Zprima() {

        Atributos atrZprima = new Atributos();
        Atributos atrL;
        if (tokenActualCoincideCualquiera("IN")) {

            printParse("97");//97.	Zprima → in ( L )
            //ASin: in
            match("IN");
            //ASin: (
            match("PARENT_ABRIR");
            //ASin: L
            atrL = L();
            //ASin: )
            match("PARENT_CERRAR");
            //ASem: Zprima.tipo:= entero
            atrZprima.setTipo("entero");
            //ASem: for i:= 1 to L.long
            //  		if (L.tipo[i] ≠ entero)
            //  			then Zprima.tipo := tipo_error
            if (!atrL.getTipo().equals("vacío")) {
                String[] lista = atrL.getTipo().split(" ");
                for (String item : lista) {
                    if (!item.equals("entero")) {
                        atrZprima.setTipo("tipo_error");
                    }
                }
            }

        } else if (tokenActualCoincideCualquiera(
                "PARENT_CERRAR", "PRODUCTO", "POTENCIA", "MAS", "COMA", "MENOS",
                "DIVISION", "PYC", "MAYOR", "MAYOR_IGUAL", "MENOR", "MENOR_IGUAL", "IGUAL",
                "DISTINTO", "AND", "DO", "IN", "MOD", "OF", "OR", "THEN", "TO", "XOR")) {  //lambda

            printParse("98"); //98.	Zprima → λ
            //ASem:Zprima.tipo = vacío
            atrZprima.setTipo("vacío");
        }

        debug(atrZprima);
        return atrZprima;
    }


    // Función auxiliar
    // Compueba que el token actual coincide con el esperado, y avanza al siguiente token
    private static Atributos match(String esperado) {

        int esperadoInt = ALex.tok_id.get(esperado);

        if (tokenActual.getId() == esperadoInt) {
            Atributos atbID = null;
            if (Objects.equals(tokenActual.getId(), ALex.tok_id.get("ID"))) {
                atbID = new Atributos();
                atbID.setPos((int) tokenActual.getAtributo());   // el índice en tabla de símbolos
            }
            if (Objects.equals(tokenActual.getId(), ALex.tok_id.get("ENTERO"))) {
                atbID = new Atributos();
                atbID.setVal((int) tokenActual.getAtributo());   // el valor de numero
            }
            if (Objects.equals(tokenActual.getId(), ALex.tok_id.get("CADENA"))) {
                atbID = new Atributos();
                atbID.setLex((String) tokenActual.getAtributo());   // el valor de la cadena
            }

            tokenActual = ALex.generarToken();
            //si generar un token da error se pone un placeholder y se lanza error
            if (tokenActual == null) {
                tokenActual = new Token(0, null);
                throw new ErrorSintacticoException("");

            }
            return atbID;
        } else {
            GestorError.writeError("sintáctico",
                    "Se esperaba " + esperado
                    + " pero se encontró " + ALex.id_tok.get(tokenActual.getId())
            );
            throw new ErrorSintacticoException("");
        }

    }

    // Función auxiliar
    // Imprime la regla sintáctica aplicada en el fichero de salida
    @SuppressWarnings("CallToPrintStackTrace")
    private static void printParse(String ReglaSintactica) {
        try {
            ptwParse.write(ReglaSintactica + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Función auxiliar
    // Comprueba que el token actual coincide con cualquiera de los tokens esperados, y gestiona el error en caso contrario
    private static boolean tokenActualCoincideCualquiera(String... codigosToken) {

        boolean result = false;

        //nombre de la funcion que lo llama
        String funcionLlamada = Thread.currentThread().getStackTrace()[2].getMethodName();

        //si cambia la funcion -> lanzar el error
        if (!huboCoincidencia && !funcionLlamada.equals(funcionAnterior)) {
            StringBuilder sb = new StringBuilder();
            sb.append("La lectura de ")
                    .append(tokenActual).append(" no respeta las reglas sintacticas.\n")
                    .append("Posibles tokens aceptados:\n");

            for (String t : TokensPosibles) {
                sb.append("\t - ").append(t).append("\n");
            }
            GestorError.writeError("sintáctico", sb.toString());
            throw new ErrorSintacticoException("");
        }

        //si es la primera vez que se entra a una funcion, se resetea el error
        if (!funcionLlamada.equals(funcionAnterior)) {
            funcionAnterior = funcionLlamada;
            TokensPosibles = new Stack<>();
            huboCoincidencia = false;
        }

        //guardar los tokens leidos
        for (String cToken : codigosToken) {
            TokensPosibles.add(cToken.toUpperCase());
        }

        //comprobar coincidencias
        for (String cToken : codigosToken) {
            boolean coincide = Objects.equals(
                    tokenActual.getId(),
                    ALex.tok_id.get(cToken.toUpperCase())
            );
            result |= coincide;
        }
        if (result == true) {
            huboCoincidencia = true;
        }

        return result;
    }

    // Función auxiliar
    // Inicializa la tabla de símbolos con los atributos necesarios para el análisis semántico
    private static void iniciarTS() {
        // TS: Activar debug y crear atr
        Procesador.gestorTS.activarDebug();
        Procesador.gestorTS.createAtributo("desplazamiento", DescripcionAtributo.DIR, TipoDatoAtributo.ENTERO);
        Procesador.gestorTS.createAtributo("etiqueta", DescripcionAtributo.ETIQUETA, TipoDatoAtributo.CADENA);
        Procesador.gestorTS.createAtributo("pasoParametros", DescripcionAtributo.MODO_PARAM, TipoDatoAtributo.LISTA);
        Procesador.gestorTS.createAtributo("tipoParametros", DescripcionAtributo.TIPO_PARAM, TipoDatoAtributo.LISTA);
        Procesador.gestorTS.createAtributo("tipoRetorno", DescripcionAtributo.TIPO_RET, TipoDatoAtributo.CADENA);
        Procesador.gestorTS.createAtributo("numParametro", DescripcionAtributo.NUM_PARAM, TipoDatoAtributo.ENTERO);
        Procesador.gestorTS.createAtributo("modoParametro", DescripcionAtributo.PARAM, TipoDatoAtributo.ENTERO);
    }

    private static void destroyTable(Tabla tabla) {
        Procesador.gestorTS.write(tabla);
        Procesador.gestorTS.destroy(tabla);
    }

    private static String generarEtiqueta() {
        return ("Etiqueta" + (numEtiq++));
    }

    public static boolean zonaDec() {
        return zonaDeclaracion;
    }

    public static boolean tsGlobal() {
        return tsGlobal;
    }

    public static void activarDebug() {
        debug = true;
    }

    public static void desactivarDebug() {
        debug = false;
    }

    public static void debug(Atributos atr) {
        if (debug) {
            String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
            System.out.println(
                    "Funcion: " + methodName
                    + " Atributo de salida: " + (atr != null ? atr.toString() : "null")
            );
        }
    }
}
