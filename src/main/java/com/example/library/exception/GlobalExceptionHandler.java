package com.example.library.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.net.URI;

/**
 * Turns exceptions thrown by controllers into friendly pages or toast messages.
 */
@ControllerAdvice(basePackages = "com.example.library.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Unknown book/member/loan id: show a "not found" page. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException ex, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return errorPage(404, "Not Found", ex.getMessage());
    }

    /** Malformed id in the URL, e.g. /books/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleBadParameter(MethodArgumentTypeMismatchException ex, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return errorPage(400, "Bad Request", "The value '" + ex.getValue() + "' is not valid for " + ex.getName() + ".");
    }

    /**
     * A business rule that was not handled by the controller itself:
     * go back to the previous page and show the reason in a toast.
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusinessRule(BusinessException ex, HttpServletRequest request) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("toastError", ex.getMessage());
        return "redirect:" + safeReturnPath(request.getHeader("Referer"));
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception ex, HttpServletResponse response) {
        log.error("Unexpected error", ex);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return errorPage(500, "Something went wrong",
                "An unexpected error occurred. Please try again or go back to the dashboard.");
    }

    private static ModelAndView errorPage(int status, String error, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", status);
        mav.addObject("error", error);
        mav.addObject("message", message);
        return mav;
    }

    /** Only redirect to a path on this site (never to an external URL taken from a header). */
    static String safeReturnPath(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/dashboard";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/dashboard";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException ex) {
            return "/dashboard";
        }
    }
}
