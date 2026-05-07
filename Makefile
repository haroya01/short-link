COMPOSE := docker compose -f resources/docker-compose.yml

.PHONY: up down run test format clean-db logs ps

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

ps:
	$(COMPOSE) ps

run:
	SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

test:
	./gradlew test

format:
	./gradlew spotlessApply

clean-db:
	$(COMPOSE) down -v
