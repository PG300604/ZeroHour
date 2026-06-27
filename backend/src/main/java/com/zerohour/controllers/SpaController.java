package com.zerohour.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    /**
     * Forward all non-API, non-static routes to React's index.html
     * This enables React Router to handle client-side routing.
     */
    @RequestMapping(value = {
        "/", "/dashboard", "/panic", "/task/**",
        "/settings", "/login", "/onboarding"
    })
    public String forwardToReact() {
        return "forward:/index.html";
    }
}
