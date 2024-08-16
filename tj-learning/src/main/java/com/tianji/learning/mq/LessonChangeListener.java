package com.tianji.learning.mq;


import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService iLearningLessonService;

    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
    exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
    key = MqConstants.Key.ORDER_PAY_KEY))
    public void onMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener 接收到了消息{}",dto.getUserId());
        // 校验
        System.out.println("mq----");
        if(dto.getUserId() == null || dto.getOrderId() == null || dto.getCourseIds() == null || dto.getCourseIds().size() == 0) {
            return;
        }
        // 调用service，保存到课程表
        iLearningLessonService.addUserLessons(dto);
    }
}
