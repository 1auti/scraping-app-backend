    package com.lautaro.service.scraping.impl;
    import com.cloudinary.Cloudinary;
    import com.lautaro.entitiy.*;
    import com.lautaro.repository.*;
    import com.lautaro.service.ImageScraper;
    import com.lautaro.service.IncrementalSaveSystem;
    import com.lautaro.service.RetryUtil;
    import com.lautaro.service.scraping.DiaService;
    import io.github.cdimascio.dotenv.Dotenv;
    import lombok.RequiredArgsConstructor;
    import org.openqa.selenium.*;
    import org.openqa.selenium.NoSuchElementException;
    import org.openqa.selenium.interactions.Actions;
    import org.openqa.selenium.support.ui.ExpectedConditions;
    import org.openqa.selenium.support.ui.WebDriverWait;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.io.IOException;
    import java.time.Duration;
    import java.time.LocalDate;
    import java.util.*;
    import java.util.concurrent.ConcurrentHashMap;

    import static com.lautaro.service.WebDriverConfig.configurarWebDriver;


    @Service
    @RequiredArgsConstructor
    public class DiaServiceImpl implements DiaService {

        private static final String DIA_BASE_URL = "https://diaonline.supermercadosdia.com.ar/";
        private static final Logger log = LoggerFactory.getLogger(DiaServiceImpl.class);
        private final IncrementalSaveSystem incrementalSaveSystem;
        private final SupermercadoRepository supermercadoRepository;
        private final CategoriaRepository categoriaRepository;
        private final SubcategoriaRepository subcategoriaRepository;
        private final TipoRepository tipoRepository;
        private final ProductoRepository productoRepository;

        @Override
        public void obtenerTodaLaInformacionDia() {
            Optional<Supermercado> supermercadoOpt = supermercadoRepository.findByNombre("Dia");
            WebDriver driver = null;
            Supermercado supermercado = new Supermercado();
            try {
                if(supermercadoOpt.isPresent()){
                    log.info("Superemrcado Dia ya existe retomando recoleccion de datos...");
                    supermercado = supermercadoOpt.get();
                }else {
                    log.info("El Supermercado no existe iniciando recoleccion de datos...");
                    supermercado.setNombre("Dia");
                    supermercado = incrementalSaveSystem.saveSupermercado(supermercado);
                }
                    driver = configurarWebDriver();
                    obtenerCategorias(supermercado);
                    supermercadoRepository.save(supermercado);
                    log.info("Información de Dia obtenida con éxito");
                } catch (Exception e) {
                    log.error("Error al obtener información de Dia", e);
                    throw new RuntimeException("Error al obtener información de Dia", e);
                } finally {
                    if (driver != null) {
                        driver.quit();
                    }
                }
        }

    /*
        @Override
        public void actualizarPreciosDIa() {
            List<Producto> productos = supermercadoService.obtenerProductosPorSupermercado("Dia");

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
            if (supermercado.getId() == null) {
                throw new IllegalStateException("El supermercado debe tener un ID antes de procesar las categorías");
            }
            WebDriver driver = configurarWebDriver();
            List<Categoria> categorias = new ArrayList<>();
            try {
                driver.get(DIA_BASE_URL);
                Thread.sleep(2000);

                WebElement categoriasButton = driver.findElement(By.cssSelector("div.diaio-custom-mega-menu-0-x-custom-mega-menu-trigger__button"));
                categoriasButton.click();

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.diaio-custom-mega-menu-0-x-category-list__container")));

                List<WebElement> categoryLinks = driver.findElements(By.cssSelector("div.diaio-custom-mega-menu-0-x-category-list__container a"));

                for (WebElement categoryLink : categoryLinks) {
                    //Buscamos si la categoria ya existe
                    Categoria categoriaAux = categoriaRepository.findByNombre(categoryLink.getText()).orElse(null);
                    if(categoriaAux != null){
                        //Revisamos si la categoria se termino de completar
                        if(categoriaAux.getCompleto()){
                            log.info("Categoria: {} ya esta completo",categoryLink.getText());
                            continue;
                        }else{
                            log.info("Categoria: {} existe pero esta incompleta",categoryLink.getText());
                            List<Subcategoria> subcategorias = obtenerSubcategorias(driver, categoryLink, categoriaAux, supermercado);
                            categoriaAux.setSubcategorias(subcategorias);
                            categoriaAux.setCompleto(true);
                            categoriaRepository.save(categoriaAux);
                            supermercado.getCategorias().add(categoriaAux);
                        }
                    }else{
                        //Si la categoria no existe se crea
                        log.info("Categoria: {} no existia en la base de datos, iniciando obtención...",categoryLink.getText());
                        Categoria nuevaCategoria = new Categoria();
                        nuevaCategoria.setNombre(categoryLink.getText());
                        nuevaCategoria.setLink(categoryLink.getAttribute("href"));
                        nuevaCategoria.setSupermercado(supermercado);
                        log.info("Categoria: {}", nuevaCategoria.getNombre());
                        nuevaCategoria = incrementalSaveSystem.saveCategoria(nuevaCategoria);
                        List<Subcategoria> subcategorias = obtenerSubcategorias(driver, categoryLink, nuevaCategoria, supermercado);
                        nuevaCategoria.setSubcategorias(subcategorias);
                        nuevaCategoria.setCompleto(true);
                        categoriaRepository.save(nuevaCategoria);
                        supermercado.getCategorias().add(nuevaCategoria);
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                driver.quit();
            }
        }

        private List<Subcategoria> obtenerSubcategorias(WebDriver driver,WebElement categoryLink, Categoria categoria, Supermercado supermercado) {
            log.info("Iniciando proceso de obtener subcategorias: {} ", categoria.getNombre());
        Actions actions = new Actions(driver);
        actions.moveToElement(categoryLink).perform();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<WebElement> subcategoryLinks = driver.findElements(By.xpath("//a[contains(@class, 'diaio-custom-mega-menu-0-x-category-list-item__container')]"));
        List<Subcategoria> subcategorias = new ArrayList<>();

        String[] palabrasExcluir = {"Almacén", "Bebidas", "Frescos","Desayuno","Limpieza","Perfumería","Congelados","Bebés y Niños","Hogar y Deco","Mascotas","Frutas y Verduras","Electro Hogar","Kiosco"};
        String regex = String.join("|", palabrasExcluir);

            for (WebElement subcategoryLink : subcategoryLinks) {
                String subcategoriaNombre = subcategoryLink.getText();
                if (!subcategoriaNombre.matches(".*(" + regex + ").*")) {
                    Subcategoria subcategoriaAux = subcategoriaRepository.findByNombre(subcategoriaNombre).orElse(null);
                    if (subcategoriaAux != null) {
                        if (subcategoriaAux.getCompleto()) {
                            log.info("La subcategoria {} esta completa", subcategoriaNombre);
                            continue;
                        } else {
                            log.info("La subcategoria {}  no esta completa, iniciando obtencion de informacion", subcategoriaNombre);
                            obtenerTipos(driver, subcategoryLink, subcategoriaAux, supermercado, categoria);
                            subcategoriaAux.setCompleto(true);
                            subcategoriaRepository.save(subcategoriaAux);
                            subcategorias.add(subcategoriaAux);
                        }
                    }else {
                    log.info("La subcategoria {}  no existe en la base de datos", subcategoriaNombre);
                    Subcategoria nuevaSubcategoria = new Subcategoria();
                    nuevaSubcategoria.setNombre(subcategoryLink.getText());
                    nuevaSubcategoria.setLink(subcategoryLink.getAttribute("href"));
                    nuevaSubcategoria.setCategoria(categoria);
                    log.info("Nombre: {}  Link : {}", nuevaSubcategoria.getNombre(), nuevaSubcategoria.getLink());
                    nuevaSubcategoria = incrementalSaveSystem.saveSubcategoria(nuevaSubcategoria);
                    //Obtenemos los tipos de la subcategoria
                    obtenerTipos(driver, subcategoryLink, nuevaSubcategoria, supermercado, categoria);
                    nuevaSubcategoria.setCompleto(true);
                    subcategoriaRepository.save(nuevaSubcategoria);
                    subcategorias.add(nuevaSubcategoria);
                }
            }
        }
        return subcategorias;
        }



        private void obtenerTipos(WebDriver driver, WebElement subcategoryLink, Subcategoria subcategoria, Supermercado supermercado, Categoria categoria) {
            log.info("Obteniendo tipos para la subcategoría: {}", subcategoria.getNombre());

            Actions actions = new Actions(driver);
            actions.moveToElement(subcategoryLink).perform();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Ha ocurrido un error en el servicio tipos");
            }

            List<WebElement> typeLinks = driver.findElements(By.cssSelector("div.diaio-custom-mega-menu-0-x-category-list__container a"));


            String[] palabrasExcluir = {
                    "Almacén", "Bebidas", "Frescos", "Desayuno", "Limpieza", "Perfumería", "Congelados", "Bebés y Niños", "Hogar y Deco",
                    "Mascotas", "Frutas y Verduras", "Electro Hogar", "Kiosco", "Conservas", "Aceite y aderezos", "Pastas secas", "Arroz y legumbres",
                    "Panaderia", "Golosinas y alfajores", "Repostería", "Comidas listas", "Harinas", "Picadas", "Pan rallado y rebozadores",
                    "Gaseosas", "Cervezas", "Aguas", "Bodega", "Jugos e isotonicas", "Leches", "Fiambrería", "Lácteos", "Carnicería",
                    "Pastas frescas", "Listos para disfrutar", "Galletitas y cereales", "Infusiones y endulzantes", "Para untar", "Cuidado de la ropa", "Papelería",
                    "Limpiadores", "Limpieza de cocina", "Accesorios de limpieza", "Desodorantes de ambiente", "Insecticidas", "Fósforos y velas",
                    "Bolsas", "Cuidado del pelo", "Cuidado personal", "Cuidado bucal", "Jabones", "Protección femenina", "Máquinas de afeitar",
                    "Farmacia", "Hamburguesas y medallones", "Rebozados", "Vegetales congelados", "Postres congelados", "Pescadería",
                    "Papas congeladas", "Comidas congeladas", "Pañales", "Cuidado del bebé", "Alimentación para bebés", "Juegos y juguetes", "Librería",
                    "Deco hogar", "Ferretería", "Cocina", "Gatos", "Perros", "Frutas y Verduras", "Huevos", "Frutos secos",
                    "Panadería", "Aceites y aderezos"
            };
            Set<String> palabrasExcluirSet = new HashSet<>(Arrays.asList(palabrasExcluir));

            for (WebElement typeLink : typeLinks) {
                String typeName = typeLink.getText();
                if (!typeName.equals("Ver todos") && !palabrasExcluirSet.contains(typeName)) {
                    Tipo tipoExistente = tipoRepository.findByNombreAndSubcategoria(typeName, subcategoria).orElse(null);
                    if(tipoExistente != null){
                        if(tipoExistente.getCompleto()){
                            log.info("El TIpo esta completo {} ",tipoExistente.getNombre());
                            continue;
                        }else{
                            log.info("El Tipo esta incompleto {}",tipoExistente.getNombre());
                            obtenerProductos(tipoExistente.getLink(),tipoExistente,supermercado,categoria,subcategoria);
                            tipoExistente.setCompleto(true);
                            subcategoria.getTipoList().add(tipoExistente);
                            tipoRepository.save(tipoExistente);
                            log.info("El Tipo se ha guardado con exito");
                            continue;
                        }
                    }else{
                        log.info("El Tipo no existe en la base de datos... {} ",typeName);
                        Tipo tipoNuevo = new Tipo();
                        tipoNuevo.setNombre(typeName);
                        tipoNuevo.setLink(typeLink.getAttribute("href"));
                        tipoNuevo.setSubcategoria(subcategoria);
                        tipoNuevo = incrementalSaveSystem.saveTipo(tipoNuevo);
                        obtenerProductos(tipoNuevo.getLink(),tipoNuevo,supermercado,categoria,subcategoria);
                        tipoNuevo.setCompleto(true);
                        subcategoria.getTipoList().add(tipoNuevo);
                        tipoRepository.save(tipoNuevo);
                        log.info("El Tipo se ha guardado con exito");
                        continue;

                    }





                }
            }

        }


        private static String extraerNombreProducto(String textoCompleto) {
            // Dividir el texto por saltos de línea y tomar la primera línea
            String primeraLinea = textoCompleto.split("\n")[0];

            // Eliminar "El Producto " o "el producto " del inicio si está presente
            String nombreProducto = primeraLinea.replaceFirst("(?i)^(El Producto |el producto )", "");

            // Extraer solo la parte antes del primer signo de peso ($) si existe
            int indexPeso = nombreProducto.indexOf("$");
            if (indexPeso != -1) {
                nombreProducto = nombreProducto.substring(0, indexPeso);
            }

            return nombreProducto.trim();
        }

        private void obtenerProductos(String tipoUrl, Tipo tipo, Supermercado supermercado, Categoria categoria, Subcategoria subcategoria) {
            log.info("Obteniendo productos");
        WebDriver driver = configurarWebDriver();
        try {
            driver.get(tipoUrl);
            Thread.sleep(2000);
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long lastHeight = (long) js.executeScript("return document.body.scrollHeight");

            while (true) {
                js.executeScript("window.scrollBy(0, 1500);");
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                long newHeight = (long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) {
                    break;
                }
                lastHeight = newHeight;
            }

            List<WebElement> productLinks = driver.findElements(By.cssSelector("a.vtex-product-summary-2-x-clearLink"));
            List<Producto> productos = new ArrayList<>();

            for (WebElement productElement : productLinks) {
                String nombreProducto = extraerNombreProducto(productElement.getText());
                Producto productoAux = productoRepository.findByNombreAndLinkExacto(nombreProducto,productElement.getAttribute("href")).orElse(null);
                if (productoAux != null && productoAux.getCompleto() == true) {
                    log.info("El Producto {} ya existe y  esta completo", productoAux.getNombre());
                    continue;

                } else if (productoAux != null && productoAux.getCompleto() == false) {
                    log.info("El Producto {} existe pero esta incompleto", productoAux.getNombre());
                    productoAux = obtenerProducto(productoAux, supermercado, categoria, subcategoria, tipo);
                    productoAux.setCompleto(true);
                    productoAux.setSupermercado(supermercado);
                    supermercado.getProductos().add(productoAux);
                    supermercado = incrementalSaveSystem.saveSupermercado(supermercado);
                    tipo.getProductoList().add(productoAux);
                    productoAux = incrementalSaveSystem.saveProducto(productoAux);
                    productos.add(productoAux);

                }else{
                    log.info("El Producto {}  no existe en  la base de datos",productElement.getText());
                    Producto nuevoProducto =new Producto();
                    nuevoProducto.setNombre(productElement.getText());
                    nuevoProducto.setLink(productElement.getAttribute("href"));
                    nuevoProducto.setTipo(tipo);
                    nuevoProducto.setSupermercado(supermercado);
                    nuevoProducto = obtenerProducto(nuevoProducto,supermercado,categoria,subcategoria,tipo);
                    nuevoProducto.setCompleto(true);
                    nuevoProducto.setSupermercado(supermercado);
                    supermercado.getProductos().add(nuevoProducto);
                    supermercado = incrementalSaveSystem.saveSupermercado(supermercado);
                    tipo.getProductoList().add(nuevoProducto);
                    nuevoProducto = incrementalSaveSystem.saveProducto(nuevoProducto);
                    productos.add(nuevoProducto);
                }

            }

            driver.quit();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        }


        private Producto obtenerProducto(Producto producto, Supermercado supermercado, Categoria categoria, Subcategoria subcategoria, Tipo tipo) {
            return RetryUtil.retry(() -> {
                log.info("Obteniendo detalles del producto: {}", producto.getLink());
                WebDriver driver = configurarWebDriver();
                try {
                    driver.get(producto.getLink());
                    Thread.sleep(2000);
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

                    Dotenv dotenv = Dotenv.load();
                    Cloudinary cloudinary = new Cloudinary(dotenv.get("CLOUDINARY_URL"));
                    cloudinary.config.secure = true;

                    String nombreCategoria = categoria.getNombre();
                    String nombreSubcategoria = subcategoria.getNombre();
                    String nombreTipo = tipo.getNombre();

                    producto.getHistorialPrecios().add(obtenerPrecio(wait, producto));

                    WebElement brandElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".vtex-store-components-3-x-productBrandName")));
                    producto.setMarca(brandElement.getText());

                    WebElement nameElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".vtex-store-components-3-x-productNameContainer")));
                    producto.setNombre(nameElement.getText());


                    ImageScraper scraper = new ImageScraper();
                    List<String> imageUrls = scraper.scrapeImages(driver);

                    for (int i = 0; i < imageUrls.size(); i++) {
                        String imageUrl = imageUrls.get(i);
                        try {
                            String nombreImagen = generarNombreImagen(producto) + "_" + (i + 1);
                            String folder = sanitizeFolderName("supermercados/Dia/" + categoria.getNombre() + "/" + subcategoria.getNombre() + "/" + tipo.getNombre() + "/" + producto.getNombre());
                            Imagen imagen = uploadImage(cloudinary, imageUrl, producto, folder, nombreImagen);
                            producto.getImagenes().add(imagen);
                        } catch (IOException e) {
                            log.error("Error al subir la imagen del producto: " + imageUrl, e);
                        }
                    }


                    try {
                        // Intentar obtener la descripción desde el contenedor específico
                        WebElement descriptionElement = driver.findElement(By.cssSelector("div.vtex-store-components-3-x-productDescriptionText > div > div[style='display:contents']"));
                        producto.setDescripcion(descriptionElement.getText());
                    } catch (NoSuchElementException e) {
                        producto.setDescripcion("No disponible");
                    }

                    producto.setFechaIngreso(LocalDate.now());

                    //Ingredientes y Valor nutricional no estan disponibles
                    producto.setIngredientes("No disponible");

                    log.info("Producto obtenido con éxito: {}", producto.getNombre());
                }catch (Exception e){
                    log.info("ERROR : {}",e.getMessage());
                }
                driver.quit();
                return producto;
            }, 3, 1000);
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
        private String generarNombreImagen(Producto producto) {
            return sanitizeString(producto.getNombre());
        }
        private Imagen uploadImage(Cloudinary cloudinary, String imageUrl, Producto producto, String folder, String nombreImagen) throws IOException {
            try {
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                options.put("folder", folder);

                String sanitizedNombreImagen = sanitizeString(nombreImagen != null ? nombreImagen : generarNombreImagen(producto));

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
        private HistorialPrecios obtenerPrecio(WebDriverWait wait , Producto producto){

            WebElement priceElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".vtex-product-price-1-x-sellingPrice")));
            String price = priceElement.getText();
            String precioAux = price.replaceAll("[^0-9,\\.]+", "").replace(',', '.');
            precioAux = precioAux.replaceAll("\\.(?=.*\\.)", ""); // Eliminar puntos adicionales
            Double precio = Double.parseDouble(precioAux);

            HistorialPrecios historialPrecios = new HistorialPrecios();
            historialPrecios.setPrecio(precio);
            historialPrecios.setFechaPrecio(LocalDate.now());
            historialPrecios.setProducto(producto);

            return historialPrecios;
        }


    }