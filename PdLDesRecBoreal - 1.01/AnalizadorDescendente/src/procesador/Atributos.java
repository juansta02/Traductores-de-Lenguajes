package procesador;

public class Atributos implements Cloneable {

    private Integer pos;
    private String tipo;
    private Integer exit;
    private String ret;
    private Integer ancho;
    private Integer longs;
    private String referencia;
    private String etiqueta;
    private Integer val;
    private String lex;
    private Integer program_count;
    private Boolean asig;

    public Atributos() {
        this.pos = null;
        this.tipo = null;
        this.exit = null;
        this.ret = null;
        this.ancho = null;
        this.longs = null;
        this.referencia = null;
        this.etiqueta = null;
        this.val = null;
        this.lex = null;
        this.program_count = null;
        this.asig = null;

    }
    
    public void setAsig(Boolean asig) {
        this.asig = asig;
    }

    public Boolean getAsig() {
        if (asig == null) {
            return false;
        }
        return asig;
    }
    

    public void setVal(Integer val) {
        this.val = val;
    }

    public Integer getVal() {
        if (val == null) {
            return 32768;
        }
        return val;
    }

    public void setLex(String lex) {
        this.lex = lex;
    }

    public String getLex() {
        if (lex == null) {
            return "";
        }
        return lex;
    }

    public void setPos(Integer pos) {
        this.pos = pos;
    }

    public Integer getPos() {
        if (pos == null) {
            return 0;

        }
        return pos;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getTipo() {
        if (tipo == null) {
            return "";
        }
        return tipo;
    }

    public void setExit(Integer exit) {
        this.exit = exit;
    }

    public Integer getExit() {
        if (exit == null) {
            return -1;
        }
        return exit;
    }

    public void setRet(String ret) {
        this.ret = ret;
    }

    public String getRet() {
        if (ret == null) {
            return "";
        }
        return ret;
    }

    public void setAncho(Integer ancho) {
        this.ancho = ancho;
    }

    public Integer getAncho() {
        if (ancho == null) {
            return -1;
        }
        return ancho;
    }

    public void setLong(Integer longs) {
        this.longs = longs;
    }

    public Integer getLong() {
        if (longs == null) {
            return -1;
        }
        return longs;
    }

    public void setReferencia(String ref) {
        this.referencia = ref;
    }

    public String getReferencia() {
        if (referencia == null) {
            return "";
        }
        return referencia;
    }

    public void setEtiqueta(String et) {
        this.etiqueta = et;
    }

    public String getEtiqueta() {
        if (etiqueta == null) {
            return "";
        }
        return etiqueta;
    }

    public void setProgramCount(Integer i) {
        this.program_count = i;
    }

    public int getProgramCount() {
        if (program_count == null) {
            return 0;
        }
        return this.program_count;
    }

    @Override
    public String toString() {
        return "Atributos{"
                + "pos=" + pos
                + ", tipo='" + tipo + '\''
                + ", exit=" + exit
                + ", ret='" + ret + '\''
                + ", ancho=" + ancho
                + ", longs=" + longs
                + ", referencia='" + referencia + '\''
                + ", etiqueta='" + etiqueta + '\''
                + ", val=" + val
                + ", lex='" + lex + '\''
                + ", program_count=" + program_count
                + '}';
    }

}
