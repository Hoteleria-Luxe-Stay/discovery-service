# Discovery Service (Eureka) - Sistema de Reservas de Hoteles

Servidor de descubrimiento de servicios basado en **Netflix Eureka**. Actúa como registro central donde todos los microservicios se registran y descubren entre sí.

## Información del Servicio

| Propiedad | Valor |
|-----------|-------|
| Puerto | 8761 |
| Java | 21 |
| Spring Boot | 3.4.0 |
| Spring Cloud | 2024.0.1 |
| Dashboard | http://localhost:8761 |

## Estructura del Proyecto

```
discovery-service/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/hotel/discovery/
│       │   └── DiscoveryServiceApplication.java
│       └── resources/
│           └── application.yml
└── target/
```

## Configuración

### application.yml

```yaml
server:
  port: ${SERVER_PORT:8761}

spring:
  application:
    name: discovery-service

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

### Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Puerto del servidor Eureka | `8761` |

## Endpoints

```bash
# Dashboard Eureka (Web UI)
GET http://localhost:8761/

# Health Check
GET http://localhost:8761/actuator/health

# Información del servidor
GET http://localhost:8761/actuator/info

# Lista de aplicaciones registradas (API)
GET http://localhost:8761/eureka/apps

# Información de una aplicación específica
GET http://localhost:8761/eureka/apps/{app-name}
```

---

## Docker

### Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /app/target/*.jar app.jar
USER spring:spring
EXPOSE 8761
ENV JAVA_OPTS="-Xms256m -Xmx512m"
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  discovery-service:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: discovery-service
    hostname: discovery-service
    ports:
      - "8761:8761"
    environment:
      - SERVER_PORT=8761
      - JAVA_OPTS=-Xms256m -Xmx512m
    networks:
      - hotel-network
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    restart: unless-stopped

networks:
  hotel-network:
    external: true
```

### docker-compose.yml (Completo con Config Server)

```yaml
version: '3.8'

services:
  config-server:
    image: config-server:latest
    container_name: config-server
    ports:
      - "8888:8888"
    environment:
      - SERVER_PORT=8888
      - CONFIG_REPO_URI=https://github.com/tu-usuario/config-repo.git
    networks:
      - hotel-network
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8888/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

  discovery-service:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: discovery-service
    hostname: discovery-service
    ports:
      - "8761:8761"
    environment:
      - SERVER_PORT=8761
    depends_on:
      config-server:
        condition: service_healthy
    networks:
      - hotel-network
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

networks:
  hotel-network:
    driver: bridge
```

### Comandos Docker

```bash
# Compilar el proyecto
mvn clean package -DskipTests

# Construir imagen
docker build -t discovery-service:latest .

# Crear red (si no existe)
docker network create hotel-network

# Ejecutar contenedor
docker run -d \
  --name discovery-service \
  --hostname discovery-service \
  -p 8761:8761 \
  --network hotel-network \
  discovery-service:latest

# Ver logs
docker logs -f discovery-service

# Verificar salud
curl http://localhost:8761/actuator/health

# Abrir dashboard
open http://localhost:8761

# Detener y eliminar
docker stop discovery-service && docker rm discovery-service
```

---

## Kubernetes

### Manifiestos

#### Deployment

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: discovery-service
  namespace: hotel-system
  labels:
    app: discovery-service
    version: v1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: discovery-service
  template:
    metadata:
      labels:
        app: discovery-service
        version: v1
    spec:
      containers:
        - name: discovery-service
          image: ${ACR_NAME}.azurecr.io/discovery-service:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8761
              name: http
          env:
            - name: SERVER_PORT
              value: "8761"
            - name: EUREKA_CLIENT_REGISTER_WITH_EUREKA
              value: "false"
            - name: EUREKA_CLIENT_FETCH_REGISTRY
              value: "false"
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8761
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8761
            initialDelaySeconds: 30
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /actuator/health
              port: 8761
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 30
```

#### Service

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: discovery-service
  namespace: hotel-system
  labels:
    app: discovery-service
spec:
  type: ClusterIP
  selector:
    app: discovery-service
  ports:
    - port: 8761
      targetPort: 8761
      protocol: TCP
      name: http
---
# Service para acceso externo al dashboard (opcional)
apiVersion: v1
kind: Service
metadata:
  name: discovery-service-external
  namespace: hotel-system
  labels:
    app: discovery-service
spec:
  type: LoadBalancer
  selector:
    app: discovery-service
  ports:
    - port: 8761
      targetPort: 8761
      protocol: TCP
      name: http
```

#### ConfigMap (Opcional para configuración adicional)

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: discovery-service-config
  namespace: hotel-system
data:
  EUREKA_INSTANCE_HOSTNAME: "discovery-service"
  EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"
```

### Alta Disponibilidad (Cluster de Eureka)

Para producción, se recomienda un cluster de Eureka con múltiples réplicas:

```yaml
# k8s/deployment-ha.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: discovery-service
  namespace: hotel-system
spec:
  serviceName: discovery-service
  replicas: 3
  selector:
    matchLabels:
      app: discovery-service
  template:
    metadata:
      labels:
        app: discovery-service
    spec:
      containers:
        - name: discovery-service
          image: ${ACR_NAME}.azurecr.io/discovery-service:latest
          ports:
            - containerPort: 8761
          env:
            - name: EUREKA_INSTANCE_HOSTNAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: EUREKA_CLIENT_REGISTER_WITH_EUREKA
              value: "true"
            - name: EUREKA_CLIENT_FETCH_REGISTRY
              value: "true"
            - name: EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE
              value: "http://discovery-service-0.discovery-service:8761/eureka,http://discovery-service-1.discovery-service:8761/eureka,http://discovery-service-2.discovery-service:8761/eureka"
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: discovery-service
  namespace: hotel-system
spec:
  clusterIP: None
  selector:
    app: discovery-service
  ports:
    - port: 8761
      name: http
```

### Comandos Kubernetes

```bash
# Crear namespace (si no existe)
kubectl create namespace hotel-system

# Desplegar discovery-service
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Verificar despliegue
kubectl get pods -n hotel-system -l app=discovery-service
kubectl get svc -n hotel-system -l app=discovery-service

# Ver logs
kubectl logs -f deployment/discovery-service -n hotel-system

# Describir pod (para debug)
kubectl describe pod -l app=discovery-service -n hotel-system

# Port-forward para acceso local al dashboard
kubectl port-forward svc/discovery-service 8761:8761 -n hotel-system

# Abrir dashboard
open http://localhost:8761

# Ver aplicaciones registradas
curl http://localhost:8761/eureka/apps

# Verificar conectividad desde otro pod
kubectl run test-curl --image=curlimages/curl -it --rm -n hotel-system -- \
  curl http://discovery-service:8761/actuator/health
```

---

## Azure

### 1. Variables de Entorno

```bash
export RESOURCE_GROUP="rg-hotel-reservas"
export LOCATION="eastus"
export ACR_NAME="acrhotelreservas"
export AKS_CLUSTER="aks-hotel-reservas"
```

### 2. Construir y Subir Imagen a ACR

```bash
# Login en ACR
az acr login --name $ACR_NAME

# Opción 1: Build local y push
mvn clean package -DskipTests
docker build -t $ACR_NAME.azurecr.io/discovery-service:v1.0.0 .
docker push $ACR_NAME.azurecr.io/discovery-service:v1.0.0

# Opción 2: Build en ACR (recomendado)
az acr build \
  --registry $ACR_NAME \
  --image discovery-service:v1.0.0 \
  --image discovery-service:latest \
  .

# Verificar imagen
az acr repository show-tags \
  --name $ACR_NAME \
  --repository discovery-service \
  --output table
```

### 3. Deployment en AKS

```yaml
# k8s/azure-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: discovery-service
  namespace: hotel-system
  labels:
    app: discovery-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: discovery-service
  template:
    metadata:
      labels:
        app: discovery-service
    spec:
      containers:
        - name: discovery-service
          image: acrhotelreservas.azurecr.io/discovery-service:v1.0.0
          ports:
            - containerPort: 8761
          env:
            - name: SERVER_PORT
              value: "8761"
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8761
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8761
            initialDelaySeconds: 30
            periodSeconds: 5
```

### 4. Desplegar

```bash
# Obtener credenciales de AKS
az aks get-credentials \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER

# Aplicar manifiestos
kubectl apply -f k8s/azure-deployment.yaml
kubectl apply -f k8s/service.yaml

# Verificar
kubectl get pods -n hotel-system -l app=discovery-service
kubectl get svc -n hotel-system -l app=discovery-service

# Ver logs
kubectl logs -f deployment/discovery-service -n hotel-system

# Port-forward para acceder al dashboard
kubectl port-forward svc/discovery-service 8761:8761 -n hotel-system
```

### 5. Azure DevOps Pipeline

```yaml
# azure-pipelines.yml
trigger:
  branches:
    include:
      - main
  paths:
    include:
      - discovery-service/**

variables:
  dockerRegistryServiceConnection: 'acr-connection'
  imageRepository: 'discovery-service'
  containerRegistry: 'acrhotelreservas.azurecr.io'
  dockerfilePath: 'discovery-service/Dockerfile'
  tag: '$(Build.BuildId)'

pool:
  vmImage: 'ubuntu-latest'

stages:
  - stage: Build
    displayName: 'Build and Push'
    jobs:
      - job: Build
        steps:
          - task: Maven@3
            displayName: 'Maven Package'
            inputs:
              mavenPomFile: 'discovery-service/pom.xml'
              goals: 'clean package'
              options: '-DskipTests'
              javaHomeOption: 'JDKVersion'
              jdkVersionOption: '1.21'

          - task: Docker@2
            displayName: 'Build and Push Image'
            inputs:
              command: buildAndPush
              repository: $(imageRepository)
              dockerfile: $(dockerfilePath)
              containerRegistry: $(dockerRegistryServiceConnection)
              tags: |
                $(tag)
                latest

  - stage: Deploy
    displayName: 'Deploy to AKS'
    dependsOn: Build
    jobs:
      - deployment: Deploy
        environment: 'production'
        strategy:
          runOnce:
            deploy:
              steps:
                - task: KubernetesManifest@0
                  displayName: 'Deploy to Kubernetes'
                  inputs:
                    action: deploy
                    kubernetesServiceConnection: 'aks-connection'
                    namespace: hotel-system
                    manifests: |
                      discovery-service/k8s/deployment.yaml
                      discovery-service/k8s/service.yaml
                    containers: |
                      $(containerRegistry)/$(imageRepository):$(tag)
```

### 6. Alternativa: Azure Spring Apps (Recomendado)

Azure Spring Apps tiene soporte nativo para Eureka:

```bash
# Crear Azure Spring Apps
az spring create \
  --name spring-hotel-reservas \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard

# Habilitar Service Registry (Eureka gestionado)
az spring application-configuration-service create \
  --name spring-hotel-reservas \
  --resource-group $RESOURCE_GROUP

# El Service Registry está automáticamente disponible
# No necesitas desplegar tu propio Eureka Server
```

---

## Orden de Inicio de Servicios

El Discovery Service debe iniciar **antes** que los demás microservicios:

```
1. Config Server (8888)      ← Primero (si se usa)
2. Discovery Service (8761)  ← Segundo
3. API Gateway (8080)        ← Tercero
4. Auth Service (8081)       ← Después
5. Hotel Service (8082)      ← Después
6. Reserva Service (8083)    ← Después
7. Notificacion Service (8084) ← Último
```

### docker-compose con orden de dependencias

```yaml
services:
  discovery-service:
    # ...
    depends_on:
      config-server:
        condition: service_healthy

  api-gateway:
    # ...
    depends_on:
      discovery-service:
        condition: service_healthy

  auth-service:
    # ...
    depends_on:
      discovery-service:
        condition: service_healthy
```

---

## Troubleshooting

### Problemas Comunes

**1. Servicios no se registran en Eureka**
```bash
# Verificar que el servicio Eureka está accesible
curl http://discovery-service:8761/actuator/health

# Verificar configuración del cliente en otros servicios
# Debe tener:
# eureka.client.service-url.defaultZone=http://discovery-service:8761/eureka
```

**2. Dashboard muestra servicios como DOWN**
```bash
# Verificar health del servicio
curl http://<service-name>:port/actuator/health

# Los servicios deben responder 200 OK en /actuator/health
```

**3. Eureka no inicia - puerto ocupado**
```bash
# Docker
docker ps | grep 8761
docker stop <container_id>

# Kubernetes
kubectl get svc -A | grep 8761
```

**4. Problemas de memoria**
```yaml
# Aumentar límites en deployment
resources:
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

**5. Servicios se desregistran constantemente**
```yaml
# Ajustar configuración de heartbeat en los clientes
eureka:
  instance:
    lease-renewal-interval-in-seconds: 30
    lease-expiration-duration-in-seconds: 90
```

### Logs y Debugging

```bash
# Docker
docker logs discovery-service --tail 100 -f

# Kubernetes
kubectl logs -f deployment/discovery-service -n hotel-system --tail=100

# Ver eventos
kubectl get events -n hotel-system --field-selector involvedObject.name=discovery-service

# Describir pod
kubectl describe pod -l app=discovery-service -n hotel-system
```

### Verificar Registros

```bash
# Ver todos los servicios registrados
curl http://localhost:8761/eureka/apps | xmllint --format -

# Ver un servicio específico
curl http://localhost:8761/eureka/apps/AUTH-SERVICE

# Formato JSON
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

---

## Ejecución Local

```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar
java -jar target/discovery-service-1.0.0-SNAPSHOT.jar

# O con Maven
mvn spring-boot:run

# Verificar
curl http://localhost:8761/actuator/health

# Abrir dashboard
open http://localhost:8761
```

---

## Configuración de Clientes Eureka

Otros microservicios deben configurarse como clientes de Eureka:

```yaml
# application.yml de cualquier microservicio
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.uuid}
```

En Docker/Kubernetes, usar:
```yaml
EUREKA_URL: http://discovery-service:8761/eureka
```
