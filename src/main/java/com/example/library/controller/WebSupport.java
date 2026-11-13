package com.example.library.controller;

import com.example.library.exception.BusinessException;
import org.springframework.validation.BindingResult;

/**
 * Small helpers shared by the controllers.
 */
final class WebSupport {

    static final String TOAST_SUCCESS = "toastSuccess";
    static final String TOAST_ERROR = "toastError";

    private WebSupport() {
    }

    /**
     * Shows a business-rule error on the form: next to the field it concerns when known,
     * otherwise as a message at the top of the form.
     */
    static void reject(BindingResult result, BusinessException ex) {
        String field = ex.getField();
        if (field != null && result.getTarget() != null && hasProperty(result, field)) {
            result.rejectValue(field, "business", ex.getMessage());
        } else {
            result.reject("business", ex.getMessage());
        }
    }

    private static boolean hasProperty(BindingResult result, String field) {
        try {
            return result.getFieldType(field) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
