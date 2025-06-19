package dev.markodojkic.legalcontractdigitizer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class ViewController {
    @GetMapping("/auth/login")
    public String loginPage() {
        return "login";  // returns templates/login.html
    }
}