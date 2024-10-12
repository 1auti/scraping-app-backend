package com.lautaro.repository;

import com.lautaro.entitiy.Subcategoria;
import com.lautaro.entitiy.Tipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TipoRepository extends JpaRepository<Tipo,Integer> {

    Optional<Tipo> findByNombre(String nombre);
    Optional<Tipo> findByNombreAndSubcategoria(String nombre, Subcategoria subcategoria);
    @Query("SELECT t.subcategoria FROM Tipo t WHERE t = :tipo")
    Subcategoria findBySubcategoria(@Param("tipo") Tipo tipo);
}
