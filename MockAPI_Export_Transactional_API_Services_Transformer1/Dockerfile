#----------------------------------------------------------------------------------------
# STAGE 1: Build the WireMock Custom Transformer using Maven
#----------------------------------------------------------------------------------------
# Use Maven with Java 17 for building the transformer JAR
FROM maven:3.8.4-openjdk-17 AS builder

# Set working directory inside the app build container
WORKDIR /app

# Copy [pom.xml file] Maven configuration first (for Docker layer caching)
COPY pom.xml .

# Download all dependencies (improves build speed on subsequent builds)
RUN mvn dependency:go-offline

# Copy application source code
COPY src ./src

# Build the transformer appliation JAR (skip tests)
RUN mvn clean package -DskipTests

#----------------------------------------------------------------------------------------
# STAGE 2: WireMock Runtime Image
#----------------------------------------------------------------------------------------

# Use official WireMock Docker image
FROM wiremock/wiremock:3.9.1

# Copy the built transformer JAR from the build stage to the final image into WireMock extensions directory
# WireMock automatically discovers extensions from this path
COPY --from=builder /app/target/*-1.0.0.jar /var/wiremock/extensions/
#COPY target/MockAPI_Export_Transactional_API_Services_Transformer2-1.0.0.jar /var/wiremock/extensions/

# Copy WireMock stub mappings
COPY mappings/ /home/wiremock/mappings/

# Copy WireMock response files
COPY __files/ /home/wiremock/__files/

# Expose the port on which WireMock app will run (internal container port)
EXPOSE 7001

#----------------------------------------------------------------------------------------
# IMPORTANT:
# - Do NOT override ENTRYPOINT
# - WireMock image already starts WireMock
# - Runtime args like --extensions are passed via docker run
#----------------------------------------------------------------------------------------
# No CMD needed! The image's entrypoint handles everything.
# We'll pass args to the container at runtime instead.
# default entrypoint of the base image starts WireMock.
# pass --extensions at runtime if you want explicit extension class list (optional)
#----------------------------------------------------------------------------------------
