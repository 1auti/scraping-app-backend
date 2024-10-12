package com.lautaro.service.scraping.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import com.lautaro.entitiy.*;
import com.lautaro.repository.*;
import com.lautaro.service.IncrementalSaveSystem;
import com.lautaro.service.scraping.JumboService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import net.bytebuddy.implementation.bind.annotation.Super;
import org.apache.poi.ss.formula.functions.T;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static com.lautaro.service.WebDriverConfig.configurarWebDriver;


@Service
@RequiredArgsConstructor
public class JumboServiceImpl implements  JumboService{

    private static final String JUMBO_BASE_URL = "https://www.jumbo.com.ar/";
    private static final Logger log = LoggerFactory.getLogger(JumboServiceImpl.class);
    //Los repository se usarán para obtener la information del producto para lograr un correcto proceso de guardado de imagen
    //Lo utilizamos para guardar todos los datos obtenidos
    private final IncrementalSaveSystem incrementalSaveSystem;
    private final SupermercadoRepository supermercadoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final TipoRepository  tipoRepository;
    private final ProductoRepository productoRepository;


    @Override
    public void obtenerTodaLaInformacionJumbo() {

        Optional<Supermercado> supermercadoOptional = supermercadoRepository.findByNombre("Jumbo");
        Supermercado supermercado = new Supermercado();
        if(supermercadoOptional.isPresent()){
            log.info("El Supermercado ya existe retomando recoleccion de datos... ");
            supermercado = supermercadoOptional.get();
        }else{
            log.info("El Supermercado no existe iniciando recoleccion de datos...");
            supermercado.setNombre("Jumbo");
            supermercado = incrementalSaveSystem.saveSupermercado(supermercado);
        }

        try {
             //obtenemos las categorias
             obtenerCategorias(supermercado);
             supermercadoRepository.save(supermercado);
             log.info("El Proceso ha terminado");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void eliminarSupermercado(Integer id) {
        supermercadoRepository.deleteById(id);
    }

    /*
        @Override
        public void actualizarPreciosJumbo() {
            List<Producto> productos = supermercadoService.obtenerProductosPorSupermercado("Jumbo");

            for (Producto producto : productos) {
                try {
                    WebDriver driver = configurarWebDriver();
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
                    driver.get(producto.getLink());
                    HistorialPrecios historialPreciosActual = obtenerPrecio(wait, producto);
                    if (!Objects.equals(historialPreciosActual.getPrecio(), producto.getHistorialPrecios().get(producto.getHistorialPrecios().size() - 1).getPrecio())) {
                        producto.getHistorialPrecios().add(historialPreciosActual);
                    }
                    driver.quit();
                } catch (Exception e) {
                    // Manejo de la excepción
                    System.err.println("Error al actualizar el precio del producto: " + e.getMessage());
                }
            }
        }
    */
    private void obtenerCategorias(Supermercado supermercado) {
        log.info("Iniciando metodo para obtener categorias");
        //Inicializamos el driver y las categorias
        WebDriver driver = configurarWebDriver();
        try {
            //Nos conectamos a la página
            driver.get(JUMBO_BASE_URL);
            Thread.sleep(2000);

            // Selecciona el botón de categorías y haz clic en él
            WebElement categoriasButton = esperarElementoVisible(driver, By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--header-category > span.vtex-menu-2-x-styledLink > div.vtex-menu-2-x-styledLinkContent"));
            categoriasButton.click();

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // Espera hasta que las categorías sean visibles
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--menu-item-secondary")));

            // Obtén todos los enlaces de categoría
            List<WebElement> categoryLinks = driver.findElements(By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--menu-item-secondary > a.vtex-menu-2-x-styledLink"));

            //Recorremos las categories para obtener los siguientes datos
            for (WebElement categoryLink : categoryLinks) {
                Categoria categoriaAux = categoriaRepository.findByNombreAndSupermercado(categoryLink.getText(),supermercado).orElse(null);
                if(categoriaAux != null){
                    if(categoriaAux.getCompleto()){
                        log.info("La Categoria {}  esta completa",categoriaAux.getNombre());
                        continue;
                    }else{
                        log.info("La Categoria {} esta incompleta",categoriaAux.getNombre());
                        obtenerSubcategorias(categoriaAux,supermercado);
                        categoriaAux.setCompleto(true);
                        categoriaRepository.save(categoriaAux);
                        supermercado.getCategorias().add(categoriaAux);
                        continue;
                    }
                }else{
                    log.info("La Categoria {} no existe en la base de datos",categoryLink.getText());
                    Categoria categoriaNueva = new Categoria();
                    categoriaNueva.setNombre(categoryLink.getText());
                    categoriaNueva.setLink(categoryLink.getAttribute("href"));
                    categoriaNueva.setSupermercado(supermercado);
                    categoriaNueva = incrementalSaveSystem.saveCategoria(categoriaNueva);
                    log.info("Nombre: {} Link {}",categoriaNueva.getNombre(), categoriaNueva.getLink());
                    obtenerSubcategorias(categoriaNueva,supermercado);
                    categoriaNueva.setCompleto(true);
                    categoriaRepository.save(categoriaNueva);
                    supermercado.getCategorias().add(categoriaNueva);
                }
            }
        } catch (Exception e) {
            System.out.println("Error al obtener categorías: " + e.getMessage());
            // O reenviar la excepción
            throw new RuntimeException(e);
        } finally {
            driver.quit();
        }
    }

    private WebElement esperarElementoVisible(WebDriver driver, By by) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    private void obtenerSubcategorias(Categoria categoria ,Supermercado supermercado) {
        log.info("Iniciando metodo para obtener subcategorias");
        WebDriver driver = configurarWebDriver();

        try {
            driver.get(JUMBO_BASE_URL);
            Thread.sleep(2000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Espera y selecciona el botón de categorías
            WebElement categoriasButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--header-category > span.vtex-menu-2-x-styledLink > div.vtex-menu-2-x-styledLinkContent")
            ));
            categoriasButton.click();

            // Espera a que el menú de categorías se expanda
            esperarElementoVisible(driver, By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--menu-item-secondary"));

            // Obtén todos los enlaces de categorías
            List<WebElement> categoryLinks = driver.findElements(
                    By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--menu-item-secondary > a.vtex-menu-2-x-styledLink")
            );

            for (WebElement categoryLink : categoryLinks) {

                // Usa JavaScript para simular el hover
                js.executeScript("arguments[0].dispatchEvent(new MouseEvent('mouseover', {'bubbles': true, 'cancelable': true}));", categoryLink);

                // Espera a que las subcategorías sean visibles
                try {
                    esperarElementoVisible(driver, By.cssSelector("div.vtex-menu-2-x-submenuWrapper--menu-item-tertiary.vtex-menu-2-x-submenuWrapper--isOpen"));
                    //Subcategories visibles
                } catch (TimeoutException e) {
                    System.out.println("Tiempo de espera agotado. Las subcategorías no se volvieron visibles.");
                    continue; // Salta a la siguiente categoría si no se puede ver el menú de subcategorías
                }

                //Pausa para permitir que el menú de subcategorías se despliegue completamente
                Thread.sleep(2000);

                //Obtiene todos los enlaces de las subcategorias
                List<WebElement> subcategoryLinks = driver.findElements(By.cssSelector("a.vtex-menu-2-x-styledLink--item-submenu-list-custom"));

                for (WebElement subcategoryLink : subcategoryLinks) {
                    try {
                        Subcategoria subcategoriaAux = subcategoriaRepository.findByNombreAndCategoria(subcategoryLink.getText(),categoria).orElse(null);
                        if(subcategoriaAux != null){
                            if (subcategoriaAux.getCompleto()){
                                log.info("La Subcategoria {} esta completa",subcategoriaAux.getNombre());
                                continue;
                            }else{
                                log.info("La Subcategoria {} esta incompleta",subcategoriaAux.getNombre());
                                obtenerTipos(subcategoriaAux,categoria,supermercado);
                                subcategoriaAux.setCompleto(true);
                                subcategoriaRepository.save(subcategoriaAux);
                                categoria.getSubcategorias().add(subcategoriaAux);
                                log.info("Informacion de la subcategoria obtenida con exito");
                                continue;
                            }
                        }else{
                            log.info("La Subcategoria {} no existe en la base de datos",subcategoryLink.getText());
                            String nombre = subcategoryLink.getText();
                            String link = subcategoryLink.getAttribute("href");
                            log.info("Subcategoria : {} Link {}",nombre,link);
                            Subcategoria subcategoria = new Subcategoria();
                            subcategoria.setNombre(nombre);
                            subcategoria.setLink(link);
                            subcategoria.setCategoria(categoria);
                            subcategoria = incrementalSaveSystem.saveSubcategoria(subcategoria);
                            obtenerTipos(subcategoria,categoria,supermercado);
                            subcategoria.setCompleto(true);
                            categoria.getSubcategorias().add(subcategoria);
                            subcategoriaRepository.save(subcategoria);
                            log.info("Informacion de la subcategoria {} fue obtenida con exito",subcategoria.getNombre());
                        }
                    } catch (StaleElementReferenceException e) {
                        System.out.println("Elemento se volvió obsoleto, continuando con el siguiente...");
                    }
                }
                // Mueve el cursor fuera del menú para cerrarlo
                js.executeScript("arguments[0].dispatchEvent(new MouseEvent('mouseout', {'bubbles': true, 'cancelable': true, 'cancelable': true}));", categoryLink);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private void obtenerTipos(Subcategoria subcategoria, Categoria categoria, Supermercado supermercado) {
        log.info("Iniciando metodo para obtener Tipos");
        //Iniciando metodo para obtener Tipos
        WebDriver driver = configurarWebDriver();

        try {
            driver.get(JUMBO_BASE_URL);
            Thread.sleep(2000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Espera y selecciona el botón de categorías
            WebElement categoriasButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--header-category > span.vtex-menu-2-x-styledLink > div.vtex-menu-2-x-styledLinkContent")
            ));
            categoriasButton.click();

            // Espera a que el menú de categorías se expanda
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--menu-item-secondary")
            ));

            // Obtén todos los enlaces de categorías
            List<WebElement> categoryLinks = driver.findElements(
                    By.cssSelector("div.vtex-menu-2-x-styledLinkContainer--menu-item-secondary > a.vtex-menu-2-x-styledLink")
            );

            boolean subcategoriaEncontrada = false;
            boolean tipoEncontrado = false;

            for (WebElement categoryLink : categoryLinks) {
                js.executeScript("arguments[0].dispatchEvent(new MouseEvent('mouseover', {'bubbles': true, 'cancelable': true}));", categoryLink);

                try {
                    wait.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("div.vtex-menu-2-x-submenuWrapper--isOpen")
                    ));
                } catch (TimeoutException e) {
                    continue;
                }

                Thread.sleep(2000);

                List<WebElement> subcategoryLinks = driver.findElements(
                        By.cssSelector("div.vtex-menu-2-x-submenuWrapper--isOpen a.vtex-menu-2-x-styledLink--submenu-custom")
                );

                for (WebElement subcategoryLink : subcategoryLinks) {
                    try {
                        wait.until(ExpectedConditions.visibilityOf(subcategoryLink));
                        String nombre = subcategoryLink.getText();
                        String link = subcategoryLink.getAttribute("href");

                        URI uri = new URI(link);

                        if (uri.toString().toLowerCase().contains(subcategoria.getLink().toString().toLowerCase())) {
                            subcategoriaEncontrada = true;
                            Tipo tipoAux = tipoRepository.findByNombreAndSubcategoria(subcategoryLink.getText(),subcategoria).orElse(null);
                            if(tipoAux != null){
                                if (tipoAux.getCompleto()){
                                    log.info("El Tipo {} esta completo",tipoAux.getNombre());
                                    continue;
                                }else{
                                    log.info("El Tipo {} esta incompleto",tipoAux.getNombre());
                                    obtenerProductos(tipoAux,categoria,supermercado,subcategoria);
                                    tipoAux.setCompleto(true);
                                    tipoRepository.save(tipoAux);
                                    subcategoria.getTipoList().add(tipoAux);
                                    log.info("La Infomacion Tipo fue obtenida con exito");
                                    continue;
                                }
                            }else{
                                log.info("El Tipo {} no existe en la base de datos, iniciadno recoleccion de datos",nombre);
                                Tipo tipo = new Tipo();
                                tipo.setNombre(nombre);
                                tipo.setLink(link);
                                tipo.setSubcategoria(subcategoria);
                                tipo = incrementalSaveSystem.saveTipo(tipo);
                                log.info("Tipo: {} Link {}",tipo.getNombre(),tipo.getLink());
                                obtenerProductos(tipo,categoria,supermercado,subcategoria);
                                tipo.setCompleto(true);
                                log.info("La Infomacion Tipo fue obtenida con exito");
                                tipoRepository.save(tipo);
                                subcategoria.getTipoList().add(tipo);
                            }

                            tipoEncontrado = true;

                        } else if (subcategoriaEncontrada && tipoEncontrado) {
                            // Si ya encontramos tipos en la subcategoría y ahora encontramos uno que no coincide,
                            // terminamos la búsqueda

                        }
                    } catch (StaleElementReferenceException e) {
                        System.out.println("Elemento se volvió obsoleto, continuando con el siguiente...");
                    }
                }

                if (subcategoriaEncontrada && !tipoEncontrado) {
                    // Si encontramos la subcategoría, pero no encontramos tipos, continuamos con la siguiente categoría
                    subcategoriaEncontrada = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }

    }

    private List<Producto> obtenerProductos(Tipo tipo , Categoria categoria, Supermercado supermercado , Subcategoria subcategoria) {
        log.info("Procesando productos");
        //Iniciando método para obtener Productos
        WebDriver driver = configurarWebDriver();
        List<Producto> productos = new ArrayList<>();
        Set<String> enlacesProcesados = new HashSet<>();

        try {
            driver.get(tipo.getLink());
            Thread.sleep(2000);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

            int paginasRecorridas = 0;
            boolean hayMasPaginas = true;

            while (paginasRecorridas < 5 && hayMasPaginas) {
                scrollDown(driver);

                // Espera a que los productos sean visibles
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("section.vtex-product-summary-2-x-container")
                ));

                Thread.sleep(3000); // Espera adicional para asegurar la carga completa de productos

                List<WebElement> productElements = driver.findElements(
                        By.cssSelector("section.vtex-product-summary-2-x-container")
                );

                boolean hayProductosRelevantes = procesarProductos(productElements, productos, enlacesProcesados, driver , tipo,subcategoria,categoria,supermercado);

                if (!hayProductosRelevantes) {
                    System.out.println("No se encontraron productos relevantes en esta página.");
                    break;
                }

                paginasRecorridas++;
                hayMasPaginas = navegarSiguientePagina(driver, wait, paginasRecorridas);

                if (hayMasPaginas) {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("section.vtex-product-summary-2-x-container")));
                    Thread.sleep(3000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        return productos;
    }


    private boolean procesarProductos(List<WebElement> productElements, List<Producto> productos, Set<String> enlacesProcesados, WebDriver driver, Tipo tipo, Subcategoria subcategoria, Categoria categoria, Supermercado supermercado) {
        boolean hayProductosRelevantes = false;

        for (WebElement productElement : productElements) {
            try {
                log.info("Procesando nuevo producto");

                // Verificar si el producto es relevante
                if (!isRelevantProduct(productElement, tipo)) {
                    log.info("Producto no relevante para el tipo {}, saltando", tipo.getNombre());
                    continue;
                }

                String link = productElement.findElement(By.cssSelector("a.vtex-product-summary-2-x-clearLink")).getAttribute("href");

                if (enlacesProcesados.contains(link)) {
                    log.info("Producto ya procesado, saltando. Link: {}", link);
                    continue;
                }

                enlacesProcesados.add(link);

                String nombre = cleanAndShortenString(productElement.findElement(By.cssSelector("h2.vtex-product-summary-2-x-productNameContainer")).getText().trim(), 100);
                String precio = productElement.findElement(By.cssSelector("div.jumboargentinaio-store-theme-1dCOMij_MzTzZOCohX1K7w")).getText().trim();
                precio = precio.replace("$", "").replace(",", "").replace(".", ""); // eliminar símbolo de moneda, coma y puntos adicionales
                Double precioDouble = Double.parseDouble(precio);
                log.info("Producto encontrado. Nombre: {}, Precio: {}", nombre, precio);
                Producto producto = productoRepository.findByNombreAndLinkExacto(nombre, link).orElse(null);

                if (producto != null) {
                    if (producto.getCompleto()) {
                        log.info("El Producto está completo en la base de datos");
                    } else if (producto.getCompleto() == false){
                        log.info("El Producto está incompleto en la base de datos");
                        producto = obtenerProducto(producto, tipo, subcategoria, categoria, supermercado);
                        producto.setCompleto(true);
                        productoRepository.save(producto);
                        supermercado.getProductos().add(producto);
                        tipo.getProductoList().add(producto);
                        tipoRepository.save(tipo);
                        supermercadoRepository.save(supermercado);
                        log.info("Tipo y Supermercado actualizados con éxito");
                        log.info("Información adicional del producto obtenida");
                    }
                } else {
                    log.info("El Producto no existe en la base de datos");
                    Producto nuevoProducto = new Producto();
                    nuevoProducto.setNombre(nombre);
                    nuevoProducto.setLink(link);
                    nuevoProducto.setTipo(tipo);
                    nuevoProducto.setSupermercado(supermercado);
                    nuevoProducto = incrementalSaveSystem.saveProducto(nuevoProducto);
                    log.info("Producto guardado inicialmente con éxito. ID: {}", nuevoProducto.getId());
                    nuevoProducto = obtenerProducto(nuevoProducto, tipo, subcategoria, categoria, supermercado);
                    nuevoProducto.setCompleto(true);
                    supermercado.getProductos().add(nuevoProducto);
                    tipo.getProductoList().add(nuevoProducto);
                    tipoRepository.save(tipo);
                    supermercadoRepository.save(supermercado);
                    log.info("Tipo y Supermercado actualizados con éxito");
                    productoRepository.save(nuevoProducto);
                }

                hayProductosRelevantes = true;
            } catch (Exception e) {
                log.error("Error al procesar un producto: {}", e.getMessage(), e);
            }
        }
        return hayProductosRelevantes;
    }

    private boolean isRelevantProduct(WebElement productElement, Tipo tipo) {
        // Verifica si el producto tiene los elementos esperados de un producto relevante
        if (productElement.findElements(By.cssSelector("h2.vtex-product-summary-2-x-productNameContainer")).size() == 0
                || productElement.findElements(By.cssSelector("div.jumboargentinaio-store-theme-1dCOMij_MzTzZOCohX1K7w")).size() == 0) {
            return false;
        }

        // Obtiene el nombre del producto
        String nombreProducto = productElement.findElement(By.cssSelector("h2.vtex-product-summary-2-x-productNameContainer")).getText().toLowerCase();

        // Verifica si el nombre del producto tiene correlación con el tipo
        return tieneCorrelacionConTipo(nombreProducto, tipo);
    }

    private boolean tieneCorrelacionConTipo(String nombreProducto, Tipo tipo) {
        // Convertir ambos a minúsculas para una comparación sin distinción de mayúsculas/minúsculas
        String nombreProductoLower = nombreProducto.toLowerCase();
        String nombreTipoLower = tipo.getNombre().toLowerCase();

        // Dividir el nombre del tipo en palabras individuales
        String[] palabrasTipo = nombreTipoLower.split("\\s+");

        for (String palabra : palabrasTipo) {
            if (palabra.length() > 4) {
                // Para palabras largas, buscamos coincidencia parcial
                String raiz = palabra.substring(0, Math.min(palabra.length(), 6));
                if (!nombreProductoLower.contains(raiz)) {
                    return false;
                }
            } else {
                // Para palabras cortas, buscamos coincidencia exacta
                if (!nombreProductoLower.contains(palabra)) {
                    return false;
                }
            }
        }

        // Si todas las palabras (o sus raíces) del tipo están presentes, el producto es relevante
        return true;
    }

    private void scrollDown(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");

        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            try {
                Thread.sleep(2000); // Espera para que la página cargue
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == lastHeight) {
                break;
            }
            lastHeight = newHeight;
        }
    }

    private boolean navegarSiguientePagina(WebDriver driver, WebDriverWait wait, int paginaActual) {
        try {
            By nextButtonSelector = By.cssSelector("button.discoargentina-search-result-custom-1-x-option-before[value='" + (paginaActual + 1) + "']");

            if (!isElementPresent(driver, nextButtonSelector)) {
                nextButtonSelector = By.cssSelector("button.discoargentina-search-result-custom-1-x-option-before:not([value='" + paginaActual + "'])");
            }

            if (!isElementPresent(driver, nextButtonSelector)) {
                nextButtonSelector = By.xpath("//button[contains(text(), 'Siguiente') or contains(text(), 'Next') or contains(@class, 'next')]");
            }

            if (!isElementPresent(driver, nextButtonSelector)) {
                System.out.println("No se encontraron más botones de paginación. Asumiendo que es la última página.");
                return false;
            }

            WebElement nextButton = wait.until(ExpectedConditions.presenceOfElementLocated(nextButtonSelector));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", nextButton);

            wait.until(ExpectedConditions.elementToBeClickable(nextButton));

            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
            } catch (Exception e) {
                nextButton.click();
            }

            wait.until(ExpectedConditions.stalenessOf(nextButton));

            return true;
        } catch (TimeoutException | NoSuchElementException | ElementClickInterceptedException e) {
            System.out.println("No se pudo navegar a la siguiente página: " + e.getMessage());
            return false;
        }
    }

    private boolean isElementPresent(WebDriver driver, By by) {
        try {
            driver.findElement(by);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public Producto obtenerProducto(Producto producto , Tipo tipo , Subcategoria subcategoria, Categoria categoria, Supermercado supermercado) {
        log.info("Obtenemos la informacion del producto {}",producto.getNombre());
        //Iniciando obtener Productos
        WebDriver driver = configurarWebDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Set your Cloudinary credentials
        Dotenv dotenv = Dotenv.load();
        Cloudinary cloudinary = new Cloudinary(dotenv.get("CLOUDINARY_URL"));
        cloudinary.config.secure = true;


        try {
            driver.get(producto.getLink());
            Thread.sleep(2000);


            // Extraer marca
            producto.setMarca(obtenerTextoElemento(driver, By.cssSelector("span.vtex-store-components-3-x-productBrandName"), "No disponible"));
            log.info("Marca: {}",producto.getMarca());

            // Extraer precio
            producto.getHistorialPrecios().add(obtenerPrecio(wait,producto));

            // Descargar imágenes del producto
            descargarImagenes(cloudinary,driver,producto,categoria,subcategoria,tipo);

            // Descargar imágenes de certificados
            descargarCertificados(driver,wait,cloudinary,categoria,subcategoria,tipo,producto);

            // Extraer descripción
            producto.setDescripcion(obtenerTextoElemento(wait, By.cssSelector("div.view-conditions_descripcion p"), "No disponible"));
            System.out.println("Descripcion: " + obtenerTextoElemento(wait, By.cssSelector("div.view-conditions_descripcion p"), "No disponible") );

            // Extraer ingredientes
            producto.setIngredientes(obtenerTextoElemento(wait, By.xpath("//div[contains(@class, 'v-c_descripcion_subtitle')]/following-sibling::article"), "No disponible"));
            System.out.println("Ingredientes: " +  obtenerTextoElemento(wait, By.xpath("//div[contains(@class, 'v-c_descripcion_subtitle')]/following-sibling::article"), "No disponible"));

            // Extraer información nutricional
            producto.setValorNutricional(obtenerValorNutricional(driver, wait));

            // Extraer especificaciones
            producto.setEspecificaciones(obtenerEspecificaciones(driver, wait));

            //Ingresamos la fecha de ingreso a la base de datos
            producto.setFechaIngreso(LocalDate.now());

            producto.setSupermercado(supermercado);

            log.info("Informacion fue obtenida con exito");

        } catch (Exception e) {
            e.printStackTrace();
        }
        driver.quit();
        return producto;
    }

    private String obtenerTextoElemento(WebDriver driver, By locator, String valorPorDefecto) {
        try {
            return driver.findElement(locator).getText();
        } catch (NoSuchElementException e) {
            return valorPorDefecto;
        }
    }

    private String obtenerTextoElemento(WebDriverWait wait, By locator, String valorPorDefecto) {
        try {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            return element.getText();
        } catch (TimeoutException e) {
            return valorPorDefecto;
        }
    }


    private List<WebElement> encontrarImagenes(WebDriver driver) {
        // Usa el selector CSS para encontrar todas las imágenes que coincidan con el patrón
        return driver.findElements(By.cssSelector("img.vtex-store-components-3-x-productImageTag"));
    }

    private void descargarImagenes(Cloudinary cloudinary, WebDriver driver, Producto producto, Categoria categoria, Subcategoria subcategoria, Tipo tipo) {
        // Primero, crear la estructura de carpetas
        crearEstructuraCarpetas(cloudinary, categoria, subcategoria, tipo, producto);

        List<WebElement> images = encontrarImagenes(driver);

        if (images.isEmpty()) {
            System.out.println("No se encontraron imágenes para el producto: " + producto.getNombre());
            return;
        }

        System.out.println("Procesando " + images.size() + " imágenes para el producto: " + producto.getNombre());

        for (int i = 0; i < images.size(); i++) {
            WebElement image = images.get(i);
            String imageUrl = image.getAttribute("src");
            System.out.println("Procesando imagen " + (i + 1) + " de " + images.size() + ": " + imageUrl);

            try {
                String nombreImagen = generarNombreImagen(producto) + "_" + (i + 1);
                String folder = "supermercados/Jumbo/" + categoria.getNombre() + "/" + subcategoria.getNombre() + "/" + tipo.getNombre() + "/" + producto.getNombre();

                Imagen imagen = uploadImage(cloudinary, imageUrl, producto, folder, nombreImagen);
                producto.getImagenes().add(imagen);
                System.out.println("Imagen añadida al producto: " + imagen.getUrl());

            } catch (IOException e) {
                System.err.println("Error al subir la imagen del producto: " + imageUrl);
                e.printStackTrace();
            }
        }
        System.out.println("Proceso de descarga y subida de imágenes completado para el producto: " + producto.getNombre());
    }

    private String sanitizeFolderName(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("La ruta de la carpeta no puede ser null o vacía");
        }

        String[] parts = folderPath.split("/");
        StringBuilder sanitized = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;

            // Sanitiza cada parte individualmente
            String sanitizedPart = part.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚñÑ0-9\\s\\-]", ""); // Mantener letras (con acentos), números, espacios y guiones
            sanitizedPart = sanitizedPart.replaceAll("\\s+", "-"); // Reemplazar espacios por guiones
            sanitizedPart = sanitizedPart.replaceAll("-+", "-"); // Eliminar guiones consecutivos

            if (!sanitized.isEmpty()) {
                sanitized.append("/");
            }
            sanitized.append(sanitizedPart);
        }

        // Limitar la longitud total de la ruta
        String result = sanitized.toString();
        if (result.length() > 100) { // Aumentamos el límite a 100 caracteres
            result = result.substring(0, 100);
        }

        if (!isValidFolderPath(result)) {
            throw new IllegalArgumentException("El nombre de la carpeta generado no es válido");
        }

        return result;
    }

    private boolean isValidFolderPath(String folderPath) {
        return folderPath != null && !folderPath.isEmpty() &&
                !folderPath.contains("?") && !folderPath.contains("&") &&
                !folderPath.contains("#") && !folderPath.contains("%") &&
                !folderPath.contains("<") && !folderPath.contains(">") &&
                !folderPath.startsWith("/") && !folderPath.endsWith("/");
    }

    private String sanitizeString(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("El input no puede ser null o vacío");
        }

        String sanitized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", ""); // Elimina acentos

        // Reemplaza caracteres no permitidos excepto letras, números, guiones y espacios
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\s\\-]", " ");

        // Reemplaza secuencias de espacios consecutivos por un guion
        sanitized = sanitized.replaceAll("\\s+", "-");

        // Elimina guiones al inicio y al final
        sanitized = sanitized.replaceAll("^-+|-+$", "");

        // Truncate el public_id a una longitud máxima
        sanitized = sanitized.substring(0, Math.min(sanitized.length(), 100)); // Aumentamos el límite a 100 caracteres



        return sanitized;
    }



    private Imagen uploadImage(Cloudinary cloudinary, String imageUrl, Producto producto, String folder, String nombreImagen) throws IOException {
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", "image");

            // Limpiar y acortar el nombre de la carpeta
            folder = cleanAndShortenString(folder, 50);
            options.put("folder", folder);

            // Generar y sanitizar el nombre de la imagen
            String sanitizedNombreImagen = cleanAndShortenString(
                    nombreImagen != null ? nombreImagen : generarNombreImagen(producto),
                    100  // Ajusta este valor según sea necesario
            );

            // Asegurarse de que el public_id completo no exceda los 255 caracteres
            String fullPublicId = folder + "/" + sanitizedNombreImagen;
            if (fullPublicId.length() > 255) {
                int excessLength = fullPublicId.length() - 255;
                sanitizedNombreImagen = sanitizedNombreImagen.substring(0, sanitizedNombreImagen.length() - excessLength - 1);
            }

            options.put("public_id", sanitizedNombreImagen);

            Map uploadResult = cloudinary.uploader().upload(imageUrl, options);

            Imagen imagen = new Imagen();
            imagen.setPublicId(uploadResult.get("public_id").toString());
            imagen.setUrl(uploadResult.get("url").toString());
            imagen.setProducto(producto);

            return imagen;
        } catch (IOException e) {
            log.error("Error al cargar la imagen: " + e.getMessage(), e);
            throw new RuntimeException("Error al cargar la imagen", e);
        }
    }

    private String generarNombreImagen(Producto producto) {
        String nombre = cleanAndShortenString(producto.getNombre(), 50);
        String marca = cleanAndShortenString(producto.getMarca(), 20);
        String tipo = cleanAndShortenString(producto.getTipo().getNombre(), 20);

        return String.format("%s-%s-%s-%s",
                nombre,
                marca,
                tipo,
                UUID.randomUUID().toString().substring(0, 8));
    }


    private String cleanAndShortenString(String input, int maxLength) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Remover caracteres especiales y espacios extras
        String cleaned = input.replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase()
                .trim();

        // Acortar si es necesario
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }

        return cleaned;
    }








    private void crearEstructuraCarpetas(Cloudinary cloudinary, Categoria categoria, Subcategoria subcategoria, Tipo tipo, Producto producto) {
        String[] carpetas = {
                "supermercados",
                "supermercados/Jumbo",
                "supermercados/Jumbo/" + categoria.getNombre(),
                "supermercados/Jumbo/" + categoria.getNombre() + "/" + subcategoria.getNombre(),
                "supermercados/Jumbo/" + categoria.getNombre() + "/" + subcategoria.getNombre() + "/" + tipo.getNombre(),
                "supermercados/Jumbo/" + categoria.getNombre() + "/" + subcategoria.getNombre() + "/" + tipo.getNombre() + "/" + producto.getNombre(),
                "supermercados/Jumbo/" + categoria.getNombre() + "/" + subcategoria.getNombre() + "/" + tipo.getNombre() + "/" + producto.getNombre()  + "/certificados"
        };

        for (String carpeta : carpetas) {
            try {
                cloudinary.api().createFolder(carpeta, ObjectUtils.emptyMap());
                System.out.println("Carpeta creada: " + carpeta);
            } catch (Exception e) {
                if (e.getMessage().contains("Folder already exists")) {
                    System.out.println("La carpeta ya existe: " + carpeta);
                } else {
                    System.out.println("Error al crear la carpeta " + carpeta + ": " + e.getMessage());
                }
            }
        }
    }



    private void descargarCertificados(WebDriver driver, WebDriverWait wait, Cloudinary cloudinary,Categoria categoria, Subcategoria subcategoria, Tipo tipo, Producto producto) {
        List<Certificado> certificadoList = new ArrayList<>();
        try {
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("div.view-conditions_certificados-container img")));
            List<WebElement> certificadoElements = driver.findElements(By.cssSelector("div[class*='certificados'] img"));
            WebElement certificadosContainer = driver.findElement(By.cssSelector("div.view-conditions_certificados-container"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", certificadosContainer);
            Thread.sleep(1000);

            for (int i = 0; i < certificadoElements.size(); i++) {
                Certificado certificadoAux = new Certificado();
                WebElement certificado = certificadoElements.get(i);
                String imageUrl = certificado.getAttribute("src");
                String nombreArchivo = "certificado_" + (i + 1);
                String folder = "supermercados/Jumbo/" + categoria.getNombre() + "/" + subcategoria.getNombre() + "/" + tipo.getNombre() + "/" + producto.getNombre() + "/certificados/";

                try {
                    // Sube la imagen a Cloudinary
                    Imagen imagen = uploadImage(cloudinary, imageUrl, producto,folder,nombreArchivo);

                    // Asigna la URL de la imagen subida a la entidad Certificado
                    certificadoAux.setProducto(producto);
                    certificadoAux.setImg(imagen.getUrl());

                    producto.getCertificadoList().add(certificadoAux);
                } catch (IOException e) {
                    System.err.println("Error al subir la imagen del certificado: " + imageUrl);
                    e.printStackTrace();
                }
            }
        } catch (TimeoutException | InterruptedException e) {
            // Si no se encuentran certificados, la lista quedará vacía
        }

    }

    private ValorNutricional obtenerValorNutricional(WebDriver driver, WebDriverWait wait) {
        //Iniciamos informacion nutricional
        ValorNutricional valorNutricional = new ValorNutricional();
        try {
            // Intentamos encontrar el elemento, con un tiempo de espera
            WebElement nutritionTab = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("view-condition_menu_info_nutricional")));
            nutritionTab.click();

            List<WebElement> nutritionRows = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.cssSelector("div.view-conditions_nutricional_table_rows ul")));
            for (WebElement row : nutritionRows) {
                List<WebElement> columns = row.findElements(By.tagName("li"));
                if (columns.size() >= 3) {
                    String nutrient = columns.get(0).getText();
                    String value = columns.get(2).getText();
                    valorNutricional.getNutrientes().put(nutrient, value);
                }
            }
        } catch (TimeoutException | NoSuchElementException e) {
            //Informacion nutricional no disponible
            log.info("Valor nutricional no encontrado");
            valorNutricional.getNutrientes().put("No disponible", "No disponible");
        } catch (Exception e) {
            log.info("Error inesperado al obtener informacion {}",e.getMessage());
            e.printStackTrace();
            valorNutricional.getNutrientes().put("Error", "Error al obtener información");
        }
        return valorNutricional;
    }

    private Map<String, String> obtenerEspecificaciones(WebDriver driver, WebDriverWait wait) {
        //Especificaciones
        Map<String, String> especificaciones = new HashMap<>();
        try {
            // Intentamos encontrar el elemento, con un tiempo de espera
            WebElement specsTab = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("view-condition_menu_especificaciones")));
            specsTab.click();

            List<WebElement> specRows = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.cssSelector("div.view-conditions_especificaciones_table_rows ul")));
            for (WebElement row : specRows) {
                List<WebElement> columns = row.findElements(By.tagName("li"));
                if (columns.size() >= 2) {
                    String spec = columns.get(0).getText();
                    String value = columns.get(1).getText();
                    log.info("{} : {}",spec,value);
                    especificaciones.put(spec, value);
                }
            }
        } catch (TimeoutException | NoSuchElementException e) {
            //Especificaciones
            log.info("No se encontro las especificaciones");
            especificaciones.put("No disponible", "No disponible");
        } catch (Exception e) {
            log.error("Error al obtener las especificaciones {}",e.getMessage());
            especificaciones.put("Error", "Error al obtener especificaciones");
        }
        return especificaciones;
    }

    private HistorialPrecios obtenerPrecio(WebDriverWait wait, Producto producto) {
        try {
            WebElement elementoPrecio = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(@class, 'jumboargentinaio-store-theme-') and contains(text(), '$')]"))
            );
            String precioAux = elementoPrecio.getText().trim();
            log.debug("Precio original extraído: '{}'", precioAux);

            Double precioDouble = parsePrecio(precioAux);
            log.debug("Precio después de parsear: {}", precioDouble);

            if (precioDouble > 0) {
                HistorialPrecios historialPrecios = new HistorialPrecios();
                historialPrecios.setPrecio(precioDouble);
                historialPrecios.setFechaPrecio(LocalDate.now());
                historialPrecios.setProducto(producto);
                log.info("Precio procesado: {} FechaPrecio: {}", historialPrecios.getPrecio(), historialPrecios.getFechaPrecio());
                return historialPrecios;
            } else {
                log.warn("El precio extraído es 0 o negativo: {}", precioDouble);
                return null;
            }
        } catch (Exception e) {
            log.error("Error al obtener el precio: {}", e.getMessage(), e);
            return null;
        }
    }

    private double parsePrecio(String precio) {
        log.debug("Precio original recibido: '{}'", precio);

        // Eliminar cualquier carácter que no sea dígito, punto o coma
        String cleanPrecio = precio.replaceAll("[^\\d.,]", "");
        log.debug("Precio después de eliminar caracteres no numéricos: '{}'", cleanPrecio);

        // Reemplazar coma por punto si hay una coma presente
        cleanPrecio = cleanPrecio.replace(',', '.');
        log.debug("Precio después de reemplazar comas por puntos: '{}'", cleanPrecio);

        // Si hay más de un punto, mantener solo el último
        int lastDotIndex = cleanPrecio.lastIndexOf('.');
        if (lastDotIndex != cleanPrecio.indexOf('.')) {
            cleanPrecio = cleanPrecio.substring(0, lastDotIndex).replace(".", "")
                    + cleanPrecio.substring(lastDotIndex);
            log.debug("Precio después de ajustar múltiples puntos: '{}'", cleanPrecio);
        }

        try {
            double result = Double.parseDouble(cleanPrecio);
            log.debug("Precio parseado exitosamente: {}", result);
            return result;
        } catch (NumberFormatException e) {
            log.error("No se pudo parsear el precio: '{}'. Error: {}", cleanPrecio, e.getMessage());
            return 0.0;
        }
    }

}
