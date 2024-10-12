package com.lautaro.service.scraping;


import com.lautaro.entitiy.Supermercado;

public interface JumboService {

    void obtenerTodaLaInformacionJumbo();
    //void actualizarPreciosJumbo();
    void eliminarSupermercado(Integer id);
}
