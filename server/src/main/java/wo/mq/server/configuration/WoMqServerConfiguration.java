package wo.mq.server.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class WoMqServerConfiguration {
//    @Bean
//    public RedisTemplate redisTemplate(){
//        RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
//        Jackson2JsonRedisSerializer<Object> redisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
//        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
//        redisSerializer.setObjectMapper(objectMapper);
//        redisTemplate.setValueSerializer(redisSerializer);
//        redisTemplate.setConnectionFactory(new LettuceConnectionFactory());
//        return redisTemplate;
//    }
}
