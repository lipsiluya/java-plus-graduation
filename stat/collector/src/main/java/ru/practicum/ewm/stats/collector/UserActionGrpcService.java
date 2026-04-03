package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.AvroUtils;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.ewm.stats.proto.collector.UserActionControllerGrpc;

import java.time.Instant;

@GrpcService
@RequiredArgsConstructor
public class UserActionGrpcService extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    @Value("${app.kafka.topics.user-actions}")
    private String userActionsTopic;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        UserActionAvro avro = UserActionAvro.newBuilder()
                .setUserId(request.getUserId())
                .setEventId(request.getEventId())
                .setActionType(mapAction(request.getActionType()))
                .setTimestamp(toInstant(request))
                .build();

        kafkaTemplate.send(userActionsTopic, String.valueOf(request.getEventId()), AvroUtils.toBytes(avro));

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private Instant toInstant(UserActionProto request) {
        if (!request.hasTimestamp()) {
            return Instant.now();
        }
        long millis = request.getTimestamp().getSeconds() * 1000L + request.getTimestamp().getNanos() / 1_000_000L;
        return Instant.ofEpochMilli(millis);
    }

    private ActionTypeAvro mapAction(ActionTypeProto type) {
        if (type == null) {
            return ActionTypeAvro.VIEW;
        }
        return switch (type) {
            case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> ActionTypeAvro.LIKE;
            case ACTION_VIEW, UNRECOGNIZED -> ActionTypeAvro.VIEW;
        };
    }
}
