public class LatLngAdapter{

    private double latitude;
    private double longitude;

    public LatLngAdapter(){
        this.latitude = 0;
        this.longitude = 0;
    }

    public LatLngAdapter(double latitude, double longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}