package com.ticketing.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitConfig {

    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String BOOKING_CONFIRMED_KEY = "booking.confirmed";
    public static final String BOOKING_CANCELLED_KEY = "booking.cancelled";

    public static final String BOOKING_CONFIRMED_QUEUE = "notification-service.booking-confirmed";
    public static final String BOOKING_CANCELLED_QUEUE = "notification-service.booking-cancelled";

    public static final String DEAD_LETTER_EXCHANGE = "booking.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "notification-service.booking-events.dlq";

    @Bean
    TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue bookingConfirmedQueue() {
        return withDeadLetter(BOOKING_CONFIRMED_QUEUE);
    }

    @Bean
    Queue bookingCancelledQueue() {
        return withDeadLetter(BOOKING_CANCELLED_QUEUE);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding bookingConfirmedBinding() {
        return BindingBuilder.bind(bookingConfirmedQueue()).to(bookingExchange()).with(BOOKING_CONFIRMED_KEY);
    }

    @Bean
    Binding bookingCancelledBinding() {
        return BindingBuilder.bind(bookingCancelledQueue()).to(bookingExchange()).with(BOOKING_CANCELLED_KEY);
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_QUEUE);
    }

    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    private static Queue withDeadLetter(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

}
