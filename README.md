# Pokesim

FireRed-style Pokémon battle simulator REST API (Java 21, Spring Boot, PostgreSQL).
Non-commercial fan project. Pokémon is a trademark of Nintendo / Creatures / GAME FREAK / The Pokémon Company.
Data from [PokéAPI](https://pokeapi.co).

> Work in progress. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Full README comes in the final milestone.

## Run the tests

    mvn verify          # runs unit tests + writes coverage to target/site/jacoco/index.html

## Run locally

    docker compose up --build
    curl localhost:8080/actuator/health
