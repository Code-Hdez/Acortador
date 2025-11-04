package edu.pucmm.eict.services;

import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.util.Database;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UrlServiceTest {

    @BeforeAll
    static void setup() {
        System.setProperty("APP_DB_MODE", "mem");
        Database.init();
    }

    @Test
    void save_and_get_url_roundtrip() {
        UrlService urlService = new UrlService();
        Usuario user = new Usuario("testuser","pwd","user");
        // no insertar user real: permitir nulo user_id
        Url u = urlService.saveUrl("https://example.com", user);
        assertNotNull(u.getShortUrl());
        assertNotNull(u.getId());
        Url loaded = urlService.getUrl(u.getShortUrl());
        assertNotNull(loaded);
        assertEquals("https://example.com", loaded.getOriginalUrl());
    }
}

