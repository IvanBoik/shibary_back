package org.boiko.shibary_back.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

// Redirects the application root to the admin panel so that opening the bare host (e.g.
// http://localhost:8080/ ) lands on the user-management UI. The admin-panel security filter chain
// then enforces login for the admin paths.
@Hidden
@Controller
class RootRedirectController {

  @GetMapping("/")
  fun redirectToAdmin(): String = "redirect:$ADMIN_PANEL_PATH"

  private companion object {
    const val ADMIN_PANEL_PATH = "/admin/users.html"
  }
}
