/**
 * Utilidades compartidas del frontend: cliente API, notificaciones (toast),
 * validación de formularios en tiempo real y estados de carga en botones.
 */

const API_BASE_URL = "http://localhost:8080/api";

/** Escapa caracteres HTML especiales antes de insertar texto dinámico con innerHTML. */
function escapeHtml(valor) {
  return String(valor ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/* ---------------------------------------------------------
   Cliente API
   --------------------------------------------------------- */
async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });

  let data = null;
  try {
    data = await response.json();
  } catch (_err) {
    data = null;
  }

  if (!response.ok) {
    const mensaje = data?.mensaje || "Ocurrió un error inesperado. Inténtalo de nuevo.";
    const error = new Error(mensaje);
    error.status = response.status;
    error.errores = data?.errores || null;
    throw error;
  }

  return data;
}

/* ---------------------------------------------------------
   Notificaciones tipo "toast" (aria-live para lectores de pantalla)
   --------------------------------------------------------- */
function getToastRegion() {
  let region = document.getElementById("toast-region");
  if (!region) {
    region = document.createElement("div");
    region.id = "toast-region";
    region.className = "toast-region";
    region.setAttribute("role", "status");
    region.setAttribute("aria-live", "polite");
    document.body.appendChild(region);
  }
  return region;
}

function showToast(mensaje, tipo = "info", duracionMs = 4500) {
  const region = getToastRegion();
  const toast = document.createElement("div");
  toast.className = `toast toast--${tipo}`;
  toast.textContent = mensaje;
  region.appendChild(toast);

  window.setTimeout(() => {
    toast.remove();
  }, duracionMs);
}

/* ---------------------------------------------------------
   Estado de carga en botones (spinner + deshabilitado)
   --------------------------------------------------------- */
function setButtonLoading(button, isLoading) {
  if (!button) return;
  button.disabled = isLoading;
  button.classList.toggle("is-loading", isLoading);
  button.setAttribute("aria-busy", String(isLoading));
}

/* ---------------------------------------------------------
   Validación de formularios en tiempo real
   --------------------------------------------------------- */
const VALIDADORES = {
  required: (value) => value.trim().length > 0 || "Este campo es obligatorio.",
  email: (value) =>
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || "Ingresa un correo electrónico válido.",
  minLength: (min) => (value) =>
    value.length >= min || `Debe tener al menos ${min} caracteres.`,
  passwordSegura: (value) => {
    const tieneMinuscula = /[a-z]/.test(value);
    const tieneMayuscula = /[A-Z]/.test(value);
    const tieneNumero = /\d/.test(value);
    const tieneEspecial = /[^A-Za-z0-9]/.test(value);
    const longitudOk = value.length >= 8;

    if (longitudOk && tieneMinuscula && tieneMayuscula && tieneNumero && tieneEspecial) {
      return true;
    }
    return "Debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial.";
  },
  match: (otherFieldId, label) => (value) => {
    const other = document.getElementById(otherFieldId);
    return (other && value === other.value) || `Debe coincidir con ${label}.`;
  },
};

/**
 * Activa validación en tiempo real (on blur + on input tras el primer error)
 * para un formulario. `rules` es un objeto { fieldId: [validadores...] }.
 */
function attachRealtimeValidation(form, rules) {
  const tocado = new Set();

  function validarCampo(fieldId) {
    const input = document.getElementById(fieldId);
    const errorEl = document.getElementById(`${fieldId}-error`);
    if (!input || !errorEl) return true;

    const validadores = rules[fieldId] || [];
    for (const validar of validadores) {
      const resultado = validar(input.value);
      if (resultado !== true) {
        input.setAttribute("aria-invalid", "true");
        errorEl.textContent = resultado;
        return false;
      }
    }
    input.removeAttribute("aria-invalid");
    errorEl.textContent = "";
    return true;
  }

  Object.keys(rules).forEach((fieldId) => {
    const input = document.getElementById(fieldId);
    if (!input) return;

    input.addEventListener("blur", () => {
      tocado.add(fieldId);
      validarCampo(fieldId);
    });

    input.addEventListener("input", () => {
      if (tocado.has(fieldId)) {
        validarCampo(fieldId);
      }
    });
  });

  function validarTodo() {
    let esValido = true;
    Object.keys(rules).forEach((fieldId) => {
      tocado.add(fieldId);
      if (!validarCampo(fieldId)) {
        esValido = false;
      }
    });
    return esValido;
  }

  return { validarTodo, validarCampo };
}

/** Mapea errores de validación devueltos por el backend a los campos del formulario. */
function aplicarErroresBackend(errores) {
  if (!errores) return;
  Object.entries(errores).forEach(([campo, mensaje]) => {
    const input = document.getElementById(campo);
    const errorEl = document.getElementById(`${campo}-error`);
    if (input) input.setAttribute("aria-invalid", "true");
    if (errorEl) errorEl.textContent = mensaje;
  });
}
