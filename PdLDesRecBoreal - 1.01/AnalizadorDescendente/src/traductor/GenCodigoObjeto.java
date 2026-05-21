package traductor;

import java.io.*;
import java.util.*;

import procesador.Procesador;

public class GenCodigoObjeto {

    private static List<Cuarteto> cuartetos;
    private static BufferedWriter writer;
    private static int contadorEtiquetas = 0;
    private static final int T = 2; // tamaño del EM(1) + PA(1), sin PC

    public GenCodigoObjeto() {
        cuartetos = new ArrayList<>();
        try {
            writer = new BufferedWriter(new FileWriter("codigo.txt"));
            // writeln("MOVE #inicio_estaticas, .IY ; apuntar IY a zona de datos
            // estáticos");
            // writer.newLine();
            // writeln("MOVE #inicio_pila, .SP ; apuntar SP al inicio de la pila");
            // writer.newLine();
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
        try {
            writeln("BR /" + etiquetaInicio);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeln(String str) {
        try {
            writer.write(str);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void traducir(Cuarteto c) {
        System.out.println("Traduciendo: " + c);
        String loop, end;

        try {
            switch (c.getOp()) {
                case "ETIQ":
                    writeln(c.getResultado() + ":");
                    break;
                case "GOTO":
                    writeln("BR /" + c.getResultado());
                    break;
                case "HALT":
                    writeln("HALT");
                    break;
                case "ASIG":
                    // revisar como poner la dirección de memoria de las variables al asignar
                    if (c.getArg2() == "1") {
                        writeln("MOVE " + c.getArg1() + ", " + c.getResultado());
                    } else if (c.getArg2() == "2") {
                        writeln("MOVE " + c.getArg1() + ", .R9");
                        writeln("MOVE .R9, " + c.getResultado());
                    }
                    break;
                case "ASIG_CAD":
                    loop = "asig_cad_" + System.nanoTime();
                    end = "asig_cad_" + (System.nanoTime() + 1);
                    writeln("MOVE " + c.getArg1() + ", .R1");
                    writeln("MOVE " + c.getResultado() + ", .R2");
                    writeln(loop + ":");
                    writeln("MOVE [.R1], [.R2]");
                    writeln("CMP [.R1], #0");
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

                    writer.flush();
                    break;

                case "GOTO_MAY":
                    writeln("CMP " + c.getArg1() + ", " + c.getArg2());
                    writeln("BP /" + c.getResultado());
                    break;
                case "RES":
                    writeln(c.getResultado() + ": RES " + c.getArg1());
                    break;
                case "ORG":
                    writeln("ORG " + c.getResultado());
                    break;
                default:
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void cerrar() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
