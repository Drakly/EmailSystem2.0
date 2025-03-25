package app.emailsystem.controller;

import app.emailsystem.dto.UserDTO;
import app.emailsystem.entity.User;
import app.emailsystem.service.UserService;
import app.emailsystem.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

@Controller
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public String showUser(@PathVariable UUID id, Model model) {
        User user = userService.getUserById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        model.addAttribute("user", user);
        return "user/view";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable UUID id, Model model) {
        User user = userService.getUserById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        model.addAttribute("user", new UserDTO(user));
        return "user/edit";
    }

    @PostMapping("/{id}/edit")
    public String updateUser(@PathVariable UUID id,
                           @Valid @ModelAttribute("user") UserDTO userDTO,
                           BindingResult result,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "user/edit";
        }

        try {
            User user = userService.updateUser(id, userDTO);
            redirectAttributes.addFlashAttribute("message", "User updated successfully");
            return "redirect:/users/" + user.getId();
        } catch (Exception e) {
            result.rejectValue("email", "error.user", e.getMessage());
            return "user/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        if (!userService.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userService.deleteUser(id);
        redirectAttributes.addFlashAttribute("message", "User deleted successfully");
        return "redirect:/users";
    }

    @GetMapping("/email/{email}")
    public String getUserByEmail(@PathVariable String email, Model model) {
        User user = userService.getUserByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        model.addAttribute("user", user);
        return "user/view";
    }
} 