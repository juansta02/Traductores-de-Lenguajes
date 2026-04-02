package procesador;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import tslib.TS_Gestor;

// Clase principal del compilador
// Se encarga de la configuración y ejecución de los distintos componentes del compilador
public class Procesador {

    public static TS_Gestor gestorTS;

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Debes introducir un fichero en argumentos para analizar");
            System.exit(1);
        }
        String testFile = args[0];

        //String testFile = "test.txt";
        // Ficheros csv
        String mtFile = "resources//MTD_data.csv";
        // Ficheros txt
        String tokensFile = "tokens.txt";
        String errorFile = "errores.txt";
        String tsFile = "ts.txt";
        String parse = "parse.txt";

        // Entrada/Salida
        BufferedReader ptrTest = null;
        BufferedWriter ptwTokens = null;
        BufferedWriter ptwError = null;
        BufferedWriter ptwParse = null;

        try {
            ptrTest = new BufferedReader(new FileReader(testFile));
            ptwTokens = new BufferedWriter(new FileWriter(tokensFile));
            ptwError = new BufferedWriter(new FileWriter(errorFile));
            ptwParse = new BufferedWriter(new FileWriter(parse));

            // Tabla de sí­mbolos
            gestorTS = new TS_Gestor(tsFile);
            gestorTS.activarDebug();
            gestorTS.createTPalabrasReservadas();

            // Configuracion Gestor de error
            GestorError.setOutputMessageFile(ptwError);

            // Configuracion Analizador léxico
            ALex.cargar_mt(mtFile);
            ALex.setInputFile(ptrTest);
            ALex.setTokenOutputFile(ptwTokens);

            // Configuracion Analizador sintactico
            ASin.setOutputParseFile(ptwParse);
            ASin.activarDebug();

            // Inicio de ejecución: Analisis lexico, sintactico y semantico sincronizado
            ASin.analizar();

            // Cierre
            ptrTest.close();
            ptwTokens.close();
            ptwError.close();
            ptwParse.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("No ha sido posible encontrar los ficheros");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error en procesos de entrada o salida");
            System.exit(1);
        }

        String currDir = System.getProperty("user.dir");

        System.out.println("Se han generado los siguientes ficheros: "
                + "\n \t - Parse en \t\t\t'" + currDir + "\\" + parse + "'"
                + "\n \t - Tokens en \t\t\t'" + currDir + "\\" + tokensFile + "'"
                + "\n \t - Tabla de sÃ­mbolos en \t'" + currDir + "\\" + tsFile + "'"
                + "\n \t - Errores en \t\t\t'" + currDir + "\\" + errorFile + "'");

    }

}
