.PHONY: build test lint clean

build:
	cd api && ./mvnw package -DskipTests
	cd worker && ./mvnw package -DskipTests
	cd web && npm run build

test:
	cd api && ./mvnw verify
	cd web && npm test -- --run

lint:
	cd web && npm run typecheck
	@echo "Java linting TBD — add Checkstyle in Week 2"

clean:
	cd api && ./mvnw clean
	cd worker && ./mvnw clean
	cd web && rm -rf dist node_modules/.vite
