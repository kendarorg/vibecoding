package org.kendar.sync.server.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for handling web UI routes.
 * This enables single-page application behavior for client-side routing.
 */
@Controller
public class WebController {

    /**
     * Forwards all non-API requests to the index page for client-side routing.
     * Excludes requests that target static resources like CSS, JS, and images.
     *
     * @return The forward path to index.html
     */
    @GetMapping(value = {
            "/users",
            "/folders",
            "/profile"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
