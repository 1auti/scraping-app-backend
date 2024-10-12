package com.lautaro.repository;

import com.lautaro.entitiy.Categoria;
import com.lautaro.entitiy.Subcategoria;
import com.lautaro.entitiy.Tipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubcategoriaRepository extends JpaRepository<Subcategoria,Integer> {

    Optional<Subcategoria> findByNombre(String Nombre);

    @Query("SELECT subcategoria.categoria FROM Subcategoria subcategoria WHERE subcategoria = :subcategoria")
    Categoria findByCategoria(@Param("subcategoria") Subcategoria subcategoria);

    Optional<Subcategoria> findByNombreAndCategoria(String Nombre,Categoria categoria);
}
