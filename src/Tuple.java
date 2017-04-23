import java.io.Serializable;

public class Tuple implements Serializable{

    private static final long serialVersionUID = 1314160120198237281L;
    private String key;
    private String value;

    public Tuple(String key, String value){
        this.key = key;
        this.value = value;
    }

    public Tuple(){}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "key = " + key + ", value = " + value;
    }
}