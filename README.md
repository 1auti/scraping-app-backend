# Proyecto de Recolección de Datos de Supermercados

Este proyecto tiene como objetivo crear una aplicación que recolecta información de productos de distintos supermercados online para luego realizar comparaciones y generar estadísticas sobre los precios, las marcas y las categorías de productos. Utilizamos Selenium para automatizar la extracción de datos, y los resultados obtenidos podrán ser utilizados para diversos análisis de mercado.

## Supemercados
- Dia
- Jumbo
- Coto

## Objetivo

El propósito de este proyecto es extraer información actualizada de productos de varias tiendas en línea, como:

- Nombre del producto
- Precio
- Categoría
- Subcategoría
- Supermercado
- Enlace al producto
- Sucursales
- Ofertas
- Promociones

Con esta información, se pretende:

1. Comparar precios entre distintos supermercados.
2. Obtener estadísticas sobre categorías de productos.
3. Analizar variaciones de precios en el tiempo.
4. Generar un reporte que facilite la toma de decisiones para consumidores.

## Requisitos
- Java 17 o superior.
- Maven 3.8 o superior.
- Google Chrome y ChromeDriver (Version 12).
- Google Portable (Para tener un Google Chrome version 12)
  -Selenium WebDriver.
  -Base de datos de gran capacidad de alamacenamiento
  -Dependencias (Maven)
  Estas son las principales dependencias utilizadas en el proyecto, las cuales están gestionadas a través de Maven en el archivo pom.xml:
  <dependencies>
  <!-- Spring Boot Dependencies -->
  <dependency>
  	<groupId>org.springframework</groupId>
  	<artifactId>spring-context</artifactId>
  </dependency>
  <dependency>
  	<groupId>org.springframework.boot</groupId>
  	<artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
  	<groupId>org.springframework.boot</groupId>
  	<artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
  	<groupId>org.springframework.boot</groupId>
  	<artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- Selenium Dependency -->
  <dependency>
  	<groupId>org.seleniumhq.selenium</groupId>
  	<artifactId>selenium-java</artifactId>
  	<version>4.11.0</version>
  </dependency>

  <!-- Apache HttpClient -->
  <dependency>
  	<groupId>org.apache.httpcomponents</groupId>
  	<artifactId>httpclient</artifactId>
  	<version>4.5.13</version>
  </dependency>

  <!-- Cloudinary for Image Management -->
  <dependency>
  	<groupId>com.cloudinary</groupId>
  	<artifactId>cloudinary-http44</artifactId>
  	<version>1.36.0</version>
  </dependency>

  <!-- Apache POI for Excel Handling -->
  <dependency>
  	<groupId>org.apache.poi</groupId>
  	<artifactId>poi-ooxml</artifactId>
  	<version>5.2.2</version>
  </dependency>

  <!-- WebDriver Manager for Selenium -->
  <dependency>
  	<groupId>io.github.bonigarcia</groupId>
  	<artifactId>webdrivermanager</artifactId>
  	<version>5.5.3</version>
  </dependency>

  <!-- PostgreSQL Database Driver -->
  <dependency>
  	<groupId>org.postgresql</groupId>
  	<artifactId>postgresql</artifactId>
  	<scope>runtime</scope>
  </dependency>

  <!-- Logback for Logging -->
  <dependency>
  	<groupId>ch.qos.logback</groupId>
  	<artifactId>logback-classic</artifactId>
  </dependency>
</dependencies>

Instalación de dependencias (Maven):
```bash
mvn clean install
```

## Cómo Ejecutar el Proyecto

Para ejecutar la aplicación sigue los siguientes pasos:

1. **Configurar WebDriver**: Asegúrate de que ChromeDriver está correctamente configurado. Si estás utilizando una versión portátil de Chrome, asegúrate de que la ruta hacia el ejecutable esté definida correctamente en tu código o variables de entorno.

2. **Compilar y ejecutar el proyecto**:
   ```bash
   mvn clean compile
   mvn exec:java
   ```
   O puedes ejecutar directamente tu archivo principal en el entorno de desarrollo.

3. **Recolección de Datos**: La aplicación navegará por las páginas de los supermercados, extrayendo información sobre los productos y guardándola en una base de datos o en archivos locales.

## Lo que Falta

Actualmente, el proyecto aún tiene algunos elementos en desarrollo:

- **Sistema de almacenamiento**: Se está considerando utilizar una base de datos relacional (MySQL, PostgreSQL) para almacenar los datos recolectados.
- **Optimización de scraping**: El scraping de algunas páginas es lento, y se está trabajando en mejorar la eficiencia.
- **Manejo mas eficiente de los recursos**
    - Utilizar herramientas como parallel stream para poder obtener los doatos de una forma mas rapida (consume mucho mas recursos)
- **Una nube que permita alojar la cantidad de imagenes que supere la capaicada de 80 GB de almacenamiento**
- Implmentar una logica Retry Utill que sirvira para pode rre intentar si falla la logica

## Impedimentos

- **Bloqueo de páginas web**: Algunas páginas pueden bloquear las solicitudes automatizadas o cambiar su estructura frecuentemente, lo que dificulta el scraping. (Solucionado)
- **Carga dinámica de contenido**: Algunas páginas cargan productos a medida que el usuario baja en la página, lo que requiere manejar el desplazamiento y la espera de elementos de manera eficiente con Selenium.
- **Cambios en la estructura HTML**: Los supermercados pueden actualizar sus páginas, lo que podría romper los selectores utilizados para extraer los datos.


## Cómo Contribuir

Si deseas colaborar en el proyecto, puedes clonar el repositorio y enviar tus propuestas de mejoras o nuevas funcionalidades mediante pull requests.

```bash
git clone https://github.com/tu-usuario/nombre-del-proyecto.git
```

---
