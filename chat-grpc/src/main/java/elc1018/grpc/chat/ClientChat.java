package elc1018.grpc.chat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import elc1018.grpc.chat.protos.ChatServiceGrpc;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import elc1018.grpc.chat.protos.ChatMessage;
import java.util.Iterator;
import java.util.Scanner;
import com.google.protobuf.Timestamp;
import elc1018.grpc.chat.protos.Ack;

public class ClientChat {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        ManagedChannel channel = ManagedChannelBuilder
        .forAddress(host, 50051)
        .usePlaintext()
        .build();

        ChatServiceGrpc.ChatServiceBlockingStub stub = ChatServiceGrpc.newBlockingStub(channel);

        // Pedir o username ao usuário
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite seu username: ");
        String username = scanner.nextLine();

        // Construir o User (padrão Builder)
        User user = User.newBuilder()
            .setUsername(username)
            .build();

        RegisterResponse resposta = stub.register(user);

        if (!resposta.getSuccess()) {
            System.out.println("Username já em uso. Encerrando.");
            channel.shutdown();
            return;
        }
        System.out.println("Registrado como: " + resposta.getUsername());

        // Iniciar thread de recebimento de mensagens
        new Thread(() -> {
            Iterator<ChatMessage> iterator = stub.receiveMessages(user);
            while (iterator.hasNext()) {
                ChatMessage msg = iterator.next();
                System.out.println("[" + msg.getFrom() + "]: " + msg.getContent());
            }
        }).start();

        // Loop de envio de mensagens
        while (true) {
            String content = scanner.nextLine();
            if (content.equalsIgnoreCase("/exit")) {
                channel.shutdownNow();
                return;
            }
            ChatMessage message = ChatMessage.newBuilder()
             .setFrom(username)
             .setContent(content)
             .setTimestamp(Timestamp.newBuilder()
                 .setSeconds(System.currentTimeMillis() / 1000)
                 .build())
             .build();
            stub.sendMessage(message);
        }
    }
}