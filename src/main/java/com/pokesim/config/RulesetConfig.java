package com.pokesim.config;

import com.pokesim.engine.Gen3Rules;
import com.pokesim.engine.Ruleset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RulesetConfig {
    /** The one place that decides which generation's mechanics this deployment plays by. */
    @Bean
    public Ruleset ruleset() {
        return new Gen3Rules();
    }
}
