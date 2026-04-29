package elc1018.grpc.chat;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class ServerChat {
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder
        .forPort(50051)           
        .addService(new ChatServiceImpl())        
        .build()
        .start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
        }));

        server.awaitTermination();
    }
}