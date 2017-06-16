import Messages.Message;
import PointAdapter.Coordinates;
import PointAdapter.LatLngAdapter;
import PointAdapter.PolylineAdapter;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.EncodedPolyline;
import com.google.maps.model.LatLng;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

//TODO finish the refactoring
//FIXME worker's cache must be stored on disk
//TODO manage connections
public class Worker implements Runnable{

    private Socket con;
    private static String ID = "192.168.1.67";
    private static int port = (getPort() == 0 ? generatePort() : getPort()); //if the port is not assigned yet, set a random port number
    private String config = "config_worker";

    private static final File cache_file = new File("worker_cache");
    /**
     * stores the rounded coordinates
     */
    private static final Hashtable<Coordinates, PolylineAdapter> cache = loadCache(); //key = coordinates, value = PointAdapter.PointAdapter.PolylineAdapter


    public Worker(Socket con){
        this.con = con;
    }

    public void setID(String id){
        ID = id;
    }

    public static String hash(){
        return ID;
    }

    @Override
    public String toString() {
        return "Worker";
    }

    private static int getPort(){
        return port;
    }

    //returns a random port number that is not currently in use
    private static int generatePort(){
        Random r = new Random(); //creates random object
        r.setSeed(System.currentTimeMillis()); //set the seed
        int port;
        while(true){
            port = r.nextInt(20000); //gets a random int from 0 to 20000
            if(port < 4001) continue; //if the number is less than 4001, picks another one
            try{
                ServerSocket listen = new ServerSocket(port); //tries to listen to this port to check if it's not in use
                listen.close(); //closes the ServerSocket(we don't need to keep it open)
                return port; //returns the number
            }catch(IOException e){
                System.err.println(Functions.getTime() + "Worker_setPort: Port " + port + " is currently in use");
            }
        }
    }

    @Override
    public void run(){
        /*try {

            System.out.println("Entered");

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(PolylineAdapter.class, new PolylineAdapterDeserializer());
            gsonBuilder.registerTypeAdapter(PolylineAdapter.class, new PolylineAdapterSerializer());
            gsonBuilder.registerTypeAdapter(LatLngAdapter.class, new LatLngAdapterDeserializer());
            gsonBuilder.registerTypeAdapter(LatLngAdapter.class, new LatLngAdapterSerializer());
            //gsonBuilder.setPrettyPrinting();
            Gson gson = gsonBuilder.create();

            ObjectInputStream in = new ObjectInputStream(con.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(con.getOutputStream());
            out.writeBoolean(true);
            out.flush();

            System.out.println("Before 1st");

            String latlng1 = in.readUTF();
            System.out.println("After 1st");
            String latlng2 = in.readUTF();

            LatLngAdapter point1 = gson.fromJson(latlng1, LatLngAdapter.class);
            LatLngAdapter point2 = gson.fromJson(latlng2, LatLngAdapter.class);

            Coordinates query = new Coordinates(point1, point2);
            //System.out.println(query);

            searchCache(query); //equals doesn't work as expected

            con.close();

        } catch (IOException e) {
            e.printStackTrace();
        }*/
        try{
            ObjectInputStream in = new ObjectInputStream(con.getInputStream());
            Message message = (Message)in.readObject();
            if(message.getRequestType() == 1){ // 1 means search locally for the route
                //query must not be rounded
                Coordinates query = message.getQuery();

                ArrayList<PolylineAdapter> response = (ArrayList<PointAdapter.PolylineAdapter>)searchCache(query);
                //query is full precision
                sendToReducer(query, response);
                sendToMaster(null);
            }else if(message.getRequestType() == 2){ //2 means search Google API for the route

                Coordinates query = message.getQuery();
                //query is full precision
                PolylineAdapter result = GoogleAPISearch(query);
                updateCache(query, result);
                sendToMaster(result, query);
            }
        }catch(IOException e){
            e.printStackTrace();
        }catch(ClassNotFoundException e){
            e.printStackTrace();
        }
    }

    private LatLng toLatLng(LatLngAdapter latLngAdapter){
        return new LatLng(latLngAdapter.getLatitude(), latLngAdapter.getLongitude());
    }

    private LatLngAdapter toLatLngAdapter(LatLng latLng){
        return new LatLngAdapter(latLng.lat, latLng.lng);
    }

    //TODO request from Google
    private PolylineAdapter GoogleAPISearch(Coordinates query){
        System.out.println("Request from Google");
        final String ApiKey = "AIzaSyAa5T-N6-BRrJZSK0xlSrWlTh-C7RjOVdY";

        final GeoApiContext context = new GeoApiContext()
                .setQueryRateLimit(3)
                .setConnectTimeout(1, TimeUnit.SECONDS)
                .setReadTimeout(1, TimeUnit.SECONDS)
                .setWriteTimeout(1, TimeUnit.SECONDS).setApiKey(ApiKey);
        PolylineAdapter polyline = new PolylineAdapter();

        //return Double.toString(query.hashCode() * Math.random());
        LatLng origin = toLatLng(query.getOrigin());
        LatLng dest = toLatLng(query.getDestination());
        DirectionsApiRequest request = DirectionsApi.newRequest(context).origin(origin).destination(dest);

        DirectionsResult result;
        try {
            result = request.await();

            if(result != null) {
                if(result.routes.length > 0){
                    EncodedPolyline encPolyline = result.routes[0].overviewPolyline;

                    for(LatLng point: encPolyline.decodePath()){
                        polyline.addPoint(toLatLngAdapter(point));
                    }
                }
            }
        } catch (ApiException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return polyline;
    }

    private void sendToMaster(PolylineAdapter result, Coordinates... query ){
        Message message = new Message();
        if(result == null){
            message.setRequestType(5); //5 means inform the master that the results have been sent to the reducer
        }else{
            message.setRequestType(6);
            message.setQuery(query[0]);
            message.setResults(result);
        }
        try{
            ObjectOutputStream Masterout = new ObjectOutputStream(con.getOutputStream());
            Masterout.writeObject(message);
            System.out.println(Functions.getTime() + "Sent data to Master: " + message.getResults());
            Masterout.flush();
        }catch (IOException e) {
            System.err.println(Functions.getTime() + "Worker_sendToMaster: There was an IO error");
        }
    }

    private void sendToReducer(Coordinates query, ArrayList<PolylineAdapter> results){
        Message message = new Message(7, query, results);
        Socket ReducerCon = null;
        while(ReducerCon == null) {
            try {
                ReducerCon = new Socket(InetAddress.getByName(Functions.getReducerIP(config)), Functions.getReducerPort(config));
                ObjectOutputStream ReducerOut = new ObjectOutputStream(ReducerCon.getOutputStream());
                ReducerOut.writeObject(message);
                ReducerOut.flush();
                System.out.println(Functions.getTime() + "Sent " + query + " " + results);
                ObjectInputStream in = new ObjectInputStream(ReducerCon.getInputStream());
                if(in.readBoolean()) break;
            }catch(UnknownHostException e){
                System.err.println(Functions.getTime() + "Worker_sendToReducer: You are trying to connect to an unknown host!");
            }catch(IOException e){
                System.err.println(Functions.getTime() + "Worker_sendToReducer: There was an IO error");
            }
        }
        System.out.println(Functions.getTime() + "Finished");
    }

    private void masterHandshake(){
        Socket handCon = null;
        while(handCon == null){
            try{
                handCon = new Socket(InetAddress.getByName(Functions.getMasterIP(config)), Functions.getMasterPort(config));
                System.out.println(handCon);
                ObjectOutputStream out = new ObjectOutputStream(handCon.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(handCon.getInputStream());
                Message message = new Message();

                in.readBoolean();

                out.writeObject(message);
                out.flush();

                if(!in.readBoolean()){
                    handCon = null;
                    continue; //just wait for the master. false input means unwanted event happened
                }

                out.writeUTF(ID);
                out.flush();

                out.writeUTF(Integer.toString(getPort()));
                out.flush();
                //message.setQuery ("Worker");
                /*ArrayList<String> data = new ArrayList<>();
                data.add(ID);
                data.add(Integer.toString(getPort()));
                message.setResults(data);
                out.writeObject(message);
                out.flush();*/
                int i = 0;
                while(!in.readBoolean()){
                    if(i % 100000 == 0){
                        System.out.println(Functions.getTime() + "Reducer has not connected yet. Waiting..." + i);
                    }
                    i++;
                }
                String reducerIp = in.readUTF();
                String reducerPort = in.readUTF();

                Functions.setReducer(reducerIp, reducerPort, config);
                System.out.println(Functions.getTime() + "Handshake Done! " + reducerIp + " " + reducerPort);
            }catch(NullPointerException e){
                System.err.println(Functions.getTime() + "Worker_masterHandshake: Null pointer occurred. Trying again");
            }catch(UnknownHostException e){
                System.err.println(Functions.getTime() + "Worker_masterHandshake: You are trying to connect to an unknown host!");
            }catch(IOException e){
                e.printStackTrace();
                System.err.println(Functions.getTime() + "Worker_masterHandshake: There was an IO error");
            }
        }
    }

    //-----DATA RELATED METHODS-----
    private void updateCache(Coordinates query, PolylineAdapter result){
        synchronized(cache){
            cache.put(query.round(), result);
        }
    }

    private List<PolylineAdapter> searchCache(Coordinates query){
        query = query.round();
        //System.out.println(query);
        List<PolylineAdapter> results = new ArrayList<>();
        //co is rounded
        for(Coordinates co: cache.keySet()){
            //query is sent rounded by the master
            if(query.equals(co)){
                results.add(cache.get(co));
                //System.out.println(new Gson().toJson(cache.get(co)));
            }
        }

        return results;
        //return cache.get(query);
    }

    private static Hashtable<Coordinates, PolylineAdapter> loadCache(){
        Hashtable<Coordinates, PolylineAdapter> c = new Hashtable<>();
        synchronized(cache_file){
            try {
                FileInputStream fi = new FileInputStream(cache_file);
                ObjectInputStream in = new ObjectInputStream(fi);
                ArrayList<PolylineAdapter> temp = (ArrayList<PolylineAdapter>)in.readObject();
                for(PolylineAdapter pl: temp){
                    Coordinates co = new Coordinates(pl.getOrigin(), pl.getDestination());
                    c.put(co.round(), pl);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return c;
    }

    public static void main(String[] args){
        System.out.println("Port = " + getPort());
        (new Worker(null)).masterHandshake();
        try{
            //ServerSocket listenSocket = new ServerSocket(getPort());
            ServerSocket listenSocket = new ServerSocket(getPort());
            while(true){
                try{
                    System.out.println(Functions.getTime() + "Waiting for connections...");
                    Socket connection = listenSocket.accept();
                    System.out.println(Functions.getTime() + "Connection accepted: " + connection.toString());
                    new Thread(new Worker(connection)).start();
                }catch(IOException e){
                    System.err.println(Functions.getTime() + "Worker_main: There was an IO error 1");
                }
            }
        }catch(IOException e){
            System.err.println(Functions.getTime() + "Worker_main: There was an IO error 2");
        }
    }
}
