package procesador;

import traductor.GenCodigoObjeto;

public class Emisor {

    public static void emite(String op, String a1, String a2, String res) {
        GenCodigoObjeto.emite(op, a1, a2, res);
    }

}