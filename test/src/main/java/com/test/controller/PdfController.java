package com.test.controller;

import com.test.service.AIRagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/pdf")
public class PdfController {

    @Autowired
    private AIRagService ragService;

    @GetMapping("/search")
    public String search() {
        String prompt = "a.pdf中主要讲了什么内容";
        return ragService.search(prompt);
    }

    @GetMapping("/add")
    public void add(){
        ragService.add();
    }
}
