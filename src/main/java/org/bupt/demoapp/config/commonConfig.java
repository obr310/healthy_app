package org.bupt.demoapp.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.bupt.demoapp.repository.RedisChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class commonConfig {
    private static final Logger log = LoggerFactory.getLogger(commonConfig.class);

    @Autowired
    private RedisChatMemory redisChatMemory;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        ChatMemoryProvider chatMemoryProvider=new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
              return MessageWindowChatMemory.builder()
                      .id(memoryId)
                      .maxMessages(20)//LLM可以看到最近20条记录
                      .chatMemoryStore(redisChatMemory)
                      .build();
            }
        };
        return chatMemoryProvider;
    }
}
