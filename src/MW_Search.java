import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;

public class MW_Search extends Master_Worker implements Runnable{

    private String worker_id;
    private String query;
    private int requestType;
    private Functions functions;

    public MW_Search(String worker_id, String query, int requestType, Functions functions) {
        this.worker_id = worker_id;
        this.query = query;
        this.requestType = requestType;
        this.functions = functions;
    }
    @Override
    public void run() {
        Message request = new Message(requestType, query);
        try{
            //System.out.println(worker_id);
            //TODO take long to throw exception. make it faster. DONE
            InetSocketAddress worker = new InetSocketAddress(InetAddress.getByName(worker_id), 4002);
            Socket WorkerCon = new Socket();
            WorkerCon.connect(worker, 3000);
            ObjectOutputStream out = new ObjectOutputStream(WorkerCon.getOutputStream());
            out.writeObject(request);
            out.flush();

            //waits for answer from the worker
            try{
                ObjectInputStream in = new ObjectInputStream(WorkerCon.getInputStream());
                request = (Message)in.readObject();
                //System.out.println("Read message at: " + System.nanoTime());
                if(request.getRequestType() == 5){
                    System.out.println(System.nanoTime() + " worker done " + worker_id);
                }else if(request.getRequestType() == 6){
                    functions.updateCache(request.getQuery(), request.getData().get(0));//data.get(0) must not contain null
                }
            }catch(NullPointerException e){
                System.err.println("MW_Search_run: Null Pointer!");
                e.printStackTrace();
            }catch(SocketTimeoutException e){
                System.err.println("MW_Search_run: Socket Time Out!");
            }catch(ClassNotFoundException e){
                System.err.println("MW_Search_run: Class Not Found");
            }

        }catch(UnknownHostException e){
            System.err.println("MW_Search_run: You are trying to connect to an unknown host!");
            e.printStackTrace();
        }catch(IOException e){
            System.err.println("MW_Search_run: There was an IO error. Host " + worker_id + " seems to be down!");
        }catch(NullPointerException e){
            System.err.println("MW_Search_run: NullPointer");
            e.printStackTrace();
        }
    }
}
