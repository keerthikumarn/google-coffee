package com.googlecoffee.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets the React app own its client-side routes when served from Spring Boot. */
@Controller
public class SpaController {
    @GetMapping({"/staff", "/staff/"})
    public String staff() {
        return "forward:/index.html";
    }
}
