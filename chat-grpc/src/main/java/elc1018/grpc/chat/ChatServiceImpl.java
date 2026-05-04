package elc1018.grpc.chat;

import elc1018.grpc.chat.protos.ChatMessage;
import elc1018.grpc.chat.protos.ChatServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import elc1018.grpc.chat.protos.User;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.Ack;
import com.google.protobuf.Timestamp;
import io.grpc.stub.ServerCallStreamObserver;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
        private final Map<String, StreamObserver<ChatMessage>> observers = new ConcurrentHashMap<>();
        private final Set<String> registeredUsers = ConcurrentHashMap.newKeySet();    
        private final LinkedBlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>(50);

        @Override
        public void register(User request,
            StreamObserver<RegisterResponse> responseObserver) {
            String username = request.getUsername();
            RegisterResponse resposta;

            // Verifica se ja existe o usuario com esse nome
            if (registeredUsers.contains(username)) {
                resposta = RegisterResponse.newBuilder()
                    .setSuccess(false)
                    .setUsername(username)
                    .build();

               
            }else{
                registeredUsers.add(username);

                resposta = RegisterResponse.newBuilder()
                    .setSuccess(true)
                    .setUsername(username)
                    .build();
            }
            
                responseObserver.onNext(resposta);
                responseObserver.onCompleted();
                return;
        }

        @Override
        public void sendMessage(ChatMessage request,
            StreamObserver<Ack> responseObserver) {

                // Verificação básica: a mensagem possui remente e algum conteúdo
                if (request.getContent() == null || request.getContent().trim().isEmpty() || 
                    request.getFrom() == null || request.getFrom().trim().isEmpty()) {
                    
                    Ack ack = Ack.newBuilder()
                        .setSuccess(false)
                        .build();
                        
                    responseObserver.onNext(ack);
                    responseObserver.onCompleted();
                    return;
                }

                // Broadcast da mensagem para todos os usuários conectados
                observers.values().forEach(observer -> {
                    if (observer != null) {
                        observer.onNext(request);
                    }
                });

                Ack ack = Ack.newBuilder()
                    .setSuccess(true)
                    .build();

                responseObserver.onNext(ack);
                responseObserver.onCompleted();

                if(messageQueue.remainingCapacity() == 0) {
                    messageQueue.poll(); // Remove o mais antigo para abrir espaço
                }

                messageQueue.offer(request);
        
        }

        @Override
        public void receiveMessages(User request,
            StreamObserver<ChatMessage> responseObserver) {
                for (ChatMessage msg : messageQueue) {
                    responseObserver.onNext(msg);
                }

                String username = request.getUsername();
                observers.put(username, responseObserver);

                Timestamp ts = Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .build();

                // Notifique os outros usuários que um novo usuário entrou
                ChatMessage joinMessage = ChatMessage.newBuilder()
                    .setFrom("System")
                    .setContent(username + " has joined the chat!")
                    .setTimestamp(ts)
                    .build();
                

                // Fazemos um cast para acessar funcionalidades extras do observer
                ServerCallStreamObserver<ChatMessage> serverObserver = 
                    (ServerCallStreamObserver<ChatMessage>) responseObserver;

                // Esse código roda automaticamente quando o cliente desconectar
                serverObserver.setOnCancelHandler(() -> {
                    Timestamp ts2 = Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .build();

                    // aqui você remove do mapa e notifica os outros
                    observers.remove(username);
                    registeredUsers.remove(username);
                    ChatMessage leaveMessage = ChatMessage.newBuilder()
                        .setFrom("System")
                        .setContent(username + " has left the chat!")
                        .setTimestamp(ts2)
                        .build();
                    observers.values().forEach(observer -> {
                        if (observer != null) {
                            observer.onNext(leaveMessage);
                        }
                    });
                });

                // Enviar a mensagem de boas vindas pra todos
                observers.values().forEach(observer -> {
                    if (observer != null) {
                        observer.onNext(joinMessage);
                    }
                });

        }
}