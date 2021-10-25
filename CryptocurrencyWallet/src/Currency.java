public class Currency {

    private String asset_id;
    private String name;
    private int type_is_crypto;
    private double price_usd;

    public Currency(String asset_id, String name, int type_is_crypto, double price_usd) {
        this.asset_id = asset_id;
        this.name = name;
        this.type_is_crypto = type_is_crypto;
        this.price_usd = price_usd;
    }

    public String getAsset_id() {
        return this.asset_id;
    }

    public String getName() {
        return this.name;
    }

    public int isCrypto() {
        return this.type_is_crypto;
    }

    public double getPrice_usd(){
        return this.price_usd;
    }
}
