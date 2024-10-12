package com.lautaro.repository;

import com.lautaro.entitiy.Categoria;
import com.lautaro.entitiy.Producto;
import com.lautaro.entitiy.Subcategoria;
import com.lautaro.entitiy.Supermercado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria,Integer> {

    Optional<Categoria> findByNombre(String Nombre);
    Optional<Categoria> findByNombreAndSupermercado(String Nombre, Supermercado supermercado);





}
