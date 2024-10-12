package com.lautaro.controller;

import com.lautaro.entitiy.*;
import com.lautaro.service.supermercado.SupermercadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/supermercado")
public class SupermercadoController {

    @Autowired
    private SupermercadoService supermercadoService;

    @GetMapping("/categorias")
    public ResponseEntity<List<Categoria>> obtenerCategorias() {
        return ResponseEntity.ok(supermercadoService.obtenerCategorias());
    }

    @GetMapping("/subcategorias")
    public ResponseEntity<List<Subcategoria>> obtenerSubcategorias() {
        return ResponseEntity.ok(supermercadoService.obtenerSubcategorias());
    }

    @GetMapping("/tipos")
    public ResponseEntity<List<Tipo>> obtenerTipos() {
        return ResponseEntity.ok(supermercadoService.obtenerTipo());
    }

    @GetMapping("/productos/{supermercado}")
    public ResponseEntity<List<Producto>> obtenerProductosPorSupermercado(@PathVariable String supermercado) {
        return ResponseEntity.ok(supermercadoService.obtenerProductosPorSupermercado(supermercado));
    }

    @GetMapping("/productos/marca/{marca}")
    public ResponseEntity<List<Producto>> buscarProductosPorMarca(@PathVariable String marca) {
        return ResponseEntity.ok(supermercadoService.buscarProductosPorMarca(marca));
    }

    @GetMapping("/excel/categoria/{categoria}")
    public ResponseEntity<Void> generarExcelPorCategoria(@PathVariable String categoria, HttpServletResponse response) {
        try {
            List<HistorialPrecios> historialPrecios = obtenerHistorialPrecios(); // Método para obtener el historial
            supermercadoService.generarExcelPorCategoria(historialPrecios, categoria);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/excel/subcategoria/{subcategoria}")
    public ResponseEntity<Void> generarExcelPorSubcategoria(@PathVariable String subcategoria, HttpServletResponse response) {
        List<HistorialPrecios> historialPrecios = obtenerHistorialPrecios(); // Método para obtener el historial
        supermercadoService.generarExcelPorSubcategoria(historialPrecios, subcategoria);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/excel/tipo/{tipo}")
    public ResponseEntity<Void> generarExcelPorTipo(@PathVariable String tipo, HttpServletResponse response) {
        List<HistorialPrecios> historialPrecios = obtenerHistorialPrecios(); // Método para obtener el historial
        supermercadoService.generarExcelPorTipo(historialPrecios, tipo);
        return ResponseEntity.ok().build();
    }

    // Método auxiliar para obtener el historial de precios (debes implementarlo según tu lógica de negocio)
    private List<HistorialPrecios> obtenerHistorialPrecios() {
        // Implementa la lógica para obtener el historial de precios
        return null;
    }
}
