package com.lautaro.repository;

import com.lautaro.entitiy.Producto;
import com.lautaro.entitiy.Supermercado;
import com.lautaro.entitiy.Tipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto,Integer> {

    Optional<Producto> findByNombre(String Nombre);
    @Query("SELECT p FROM Producto p WHERE p.nombre = :nombre AND p.link = :link")
    Optional<Producto> findByNombreAndLinkExacto(@Param("nombre") String nombre, @Param("link") String link);




    Boolean  existsByNombre(String noombre);
    @Query("SELECT EXISTS (SELECT 1 FROM Producto p WHERE p.nombre = :nombre AND p.supermercado.nombre LIKE %:supermercado%)")
    Boolean existsByNombreAndSupermercado(@Param("nombre") String nombre, @Param("supermercado") String supermercado);
    List<Producto> findBySupermercado(Supermercado Supermercado);
    List<Producto> findByMarca(String marca);

    @Query("SELECT p.tipo FROM Producto p WHERE p = :producto")
    Tipo findByTipo(@Param("producto") Producto producto);



}

