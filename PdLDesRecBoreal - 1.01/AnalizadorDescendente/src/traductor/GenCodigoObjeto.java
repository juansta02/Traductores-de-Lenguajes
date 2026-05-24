package traductor;

import java.io.*;
import java.util.*;

import procesador.Procesador;

public class GenCodigoObjeto {

    // buffers
    private static StringBuilder zonaDatos = new StringBuilder();
    private static StringBuilder zonaCodigo = new StringBuilder();
    private static StringBuilder zonaInicio = new StringBuilder();
    private static StringBuilder zonaProcedimientos = new StringBuilder();

    private static List<Cuarteto> cuartetos;
    private static int contadorEtiquetas = 0;
    private static final int T = 2; // tamaño del EM(1) + PA(1), sin PC
    private static boolean enProcedimiento = false; // Por defecto estamos en el programa principal
    private static int contadorParametros = 0;
    private static int tamVariablesLocales = 0;

    public GenCodigoObjeto() {
        cuartetos = new ArrayList<>();
        zonaDatos.setLength(0);
        zonaCodigo.setLength(0);
        zonaInicio.setLength(0);
        zonaProcedimientos.setLength(0);
    }

    public static void emitirDato(String instruccion) {
        zonaDatos.append(instruccion).append("\n");
    }

    public static void emitirInicio(String instruccion) {
        zonaInicio.append(instruccion).append("\n");
    }

    public static void emitirProcedimiento(String instruccion) {
        zonaProcedimientos.append(instruccion).append("\n");
    }

    public static void writeln(String instruccion) {
        if (enProcedimiento) {
            zonaProcedimientos.append(instruccion).append("\n");
        } else {
            zonaCodigo.append(instruccion).append("\n");
        }
    }

    public static void setEnProcedimiento(boolean estado) {
        enProcedimiento = estado;
    }

    public static void setTamVariablesLocales(int tamano) {
        tamVariablesLocales = tamano;
    }

    private static int calcularCeldasOcupadas(String bloqueTexto) {
        int celdas = 0;
        String[] lineas = bloqueTexto.split("\n");

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty() || linea.startsWith(";")) {
                continue;
            }
            if (linea.endsWith(":") && !linea.contains(" ")) {
                continue;
            }
            if (linea.contains("RES ")) {
                try {
                    String partes[] = linea.split("RES ");
                    celdas += Integer.parseInt(partes[1].trim());
                } catch (Exception e) {
                    celdas += 1; // Por si acaso
                }
                continue;
            }
            celdas++;
        }
        return celdas;
    }

    public static void volcarAlArchivo(String rutaFichero) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(rutaFichero))) {
            int baseCodigo = 200;
            int celdasCodigoPrincipal = calcularCeldasOcupadas(zonaCodigo.toString());
            int celdasProcedimientos = calcularCeldasOcupadas(zonaProcedimientos.toString());
            int orgPilaDinamico = (((baseCodigo + celdasCodigoPrincipal + celdasProcedimientos) + 99) / 100) * 100;
            if (orgPilaDinamico == (baseCodigo + celdasCodigoPrincipal + celdasProcedimientos)) {
                orgPilaDinamico += 100;
            }
            StringBuilder pila = new StringBuilder();
            pila.append("ORG ").append(orgPilaDinamico).append("\n");
            pila.append("inicio_pila:\n");

            pw.print(zonaInicio.toString());
            pw.print(zonaDatos.toString());
            pw.print(zonaCodigo.toString());
            pw.print(zonaProcedimientos.toString());
            pw.print(pila.toString());
            contadorParametros = 0;
            System.out.println("Archivo ensamblador generado con éxito.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String nuevaEtiqueta() {
        return "L" + (contadorEtiquetas++);
    }

    public static void emite(String op, String a1, String a2, String res) {
        Cuarteto c = new Cuarteto(op, a1, a2, res);
        cuartetos.add(c);
        traducir(c);
    }

    public static void emitirSaltoInicial(String etiquetaInicio) {
        writeln("BR /" + etiquetaInicio);

    }

    private static void traducir(Cuarteto c) {
        System.out.println("Traduciendo: " + c);
        String loop, end;

        switch (c.getOp()) {
            case "ETIQ":
                if (c.getArg2().equals("3")) {
                    emitirInicio(c.getResultado() + ":");
                    emitirInicio("RES " + c.getArg1());
                } else if (c.getArg2().equals("2")) {
                    emitirInicio(c.getResultado() + ":");

                } else {
                    writeln(c.getResultado() + ":");
                }
                break;
            case "GOTO":
                if (c.getArg2().equals("3")) {
                    emitirInicio("BR /" + c.getResultado());
                } else {
                    writeln("BR /" + c.getResultado());
                }
                break;
            case "HALT":
                writeln("HALT");
                break;
            case "ASIG":
                // revisar como poner la dirección de memoria de las variables al asignar
                if (c.getArg2().equals("1")) {
                    writeln("MOVE " + c.getArg1() + ", " + c.getResultado());
                } else if (c.getArg2().equals("2")) {
                    writeln("MOVE " + c.getArg1() + ", .R9");
                    writeln("MOVE .R9, " + c.getResultado());
                } else if (c.getArg2().equals("3")) {
                    emitirInicio("MOVE " + c.getArg1() + ", " + c.getResultado());
                }
                break;
            case "ASIG_CAD":
                loop = "asig_cad_" + System.nanoTime();
                end = "asig_cad_" + (System.nanoTime() + 1);
                writeln("MOVE #" + c.getArg1() + ", .R1");
                writeln("MOVE " + c.getResultado() + ", .R2");
                writeln(loop + ":");
                writeln("MOVE [.R1], .R3");
                writeln("MOVE .R3, [.R2]");
                writeln("CMP .R3, #0");
                writeln("BZ /" + end);
                writeln("INC .R1");
                writeln("INC .R2");
                writeln("BR /" + loop);
                writeln(end + ":");
                break;
            case "SUMA":
                writeln("ADD " + c.getArg1() + ", " + c.getArg2());
                writeln("MOVE .A, " + c.getResultado());
                break;
            case "NOT":
                writeln("NOT " + c.getArg1());
                writeln("MOVE .A, " + c.getResultado());
                break;
            case "POW":
                String a = c.getArg1();
                String b = c.getArg2();
                String r = c.getResultado();

                loop = "pow_" + System.nanoTime();
                end = "pow_" + (System.nanoTime() + 1);

                // r = 1
                writeln("XOR " + r + ", " + r);
                writeln("MOVE #1, " + r);

                // contador = b
                writeln("MOVE " + b + ", .R1");

                // ponemos el operando1 en R2
                writeln("MOVE " + a + ", .R2");

                // etiqueta inicio bucle
                writeln(loop + ":");

                // si R1 == 0 salir
                writeln("CMP #1, .R1");
                writeln("BZ /" + end);

                // registro acumulador = ap1 * R2 (R2 contiene acumulación de operaciones)
                writeln("MUL " + a + ", .R2");

                // movemos el resultado a R2
                writeln("MOVE .A, .R2");

                // R1--
                writeln("SUB .R1, #1");
                writeln("MOVE .A, .R1");

                // salto al inicio
                writeln("BR /" + loop);

                // fin
                writeln(end + ":");

                // finalmente dejamos el resultado en la dirección correspondiente
                writeln("MOVE .R2, " + r);

                break;

            case "GOTO_MAY":
                writeln("CMP " + c.getArg1() + ", " + c.getArg2());
                writeln("BP /" + c.getResultado());
                break;
            case "RES":

                if (c.getArg2().equals("inicio")) {
                    emitirDato(c.getResultado() + ":");
                    emitirDato("RES " + c.getArg1());
                } else {
                    writeln(c.getResultado() + ":");
                    writeln("RES " + c.getArg1());
                }
                break;
            case "ORG":
                if (c.getArg2().equals("inicio")) {
                    emitirInicio("ORG " + c.getResultado());
                } else {
                    writeln("ORG " + c.getResultado());
                }
                break;
            case "DATA":
                emitirDato(c.getArg1() + ":");
                emitirDato("DATA \"" + c.getResultado() + "\", 0");
                break;
            case "CALL":
                writeln("CALL /" + c.getArg1());
                if (contadorParametros > 0) {
                    writeln("SUB .SP, #" + contadorParametros);
                    writeln("MOVE .A, .SP");
                }
                contadorParametros = 0;
                break;
            case "PARAM":
                contadorParametros++;
                writeln("ADD .SP, #1");
                writeln("MOVE .A, .SP");
                if (c.getArg1().startsWith("#")) {
                    writeln("MOVE " + c.getArg1() + ", [.SP]");
                } else {
                    writeln("MOVE " + c.getArg1() + ", .R9");
                    writeln("MOVE .R9, [.SP]");
                }
                break;
            case "PARAM_CAD":
                contadorParametros++;
                writeln("ADD .SP, #1");
                writeln("MOVE .A, .SP");
                writeln("MOVE " + c.getArg1() + ", [.SP]");
                break;
            case "PARAM_REF":
                contadorParametros++;
                writeln("ADD .SP, #1");
                writeln("MOVE .A, .SP");
                writeln("MOVE " + c.getArg1() + ", [.SP]");
                break;
            case "RETURN":
                if (tamVariablesLocales > 0) {
                    writeln("SUB .SP, #" + tamVariablesLocales);
                    writeln("MOVE .A, .SP");
                }
                writeln("RET");
                tamVariablesLocales = 0;
                break;
            default:
                break;
        }

    }

    public void cerrar() {
        volcarAlArchivo("codigo.txt");
    }

}
