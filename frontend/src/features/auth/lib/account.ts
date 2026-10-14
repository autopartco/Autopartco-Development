import type { CustomerRegistration } from '../api/types'
import { birthDateError, compact, emailError, newPasswordError, requiredText, type FieldErrors } from './validation'

/** Campos de `RegisterRequest`, en el orden en que se muestran (y se enfocan si hay error). */
export const ACCOUNT_FIELDS = ['name', 'surname', 'identification', 'dateBirth', 'numberPhone', 'email', 'password', 'passwordConfirm'] as const

export function readAccount(data: FormData, prefix = ''): CustomerRegistration {
  const read = (field: string) => String(data.get(`${prefix}${field}`) ?? '')
  return {
    name: read('name').trim(),
    surname: read('surname').trim(),
    identification: read('identification').trim(),
    dateBirth: read('dateBirth'),
    numberPhone: read('numberPhone').trim(),
    email: read('email').trim(),
    password: read('password'),
  }
}

/** Confirmación de contraseña: solo existe en el formulario; `RegisterRequest` no la recibe. */
export function readPasswordConfirm(data: FormData, prefix = ''): string {
  return String(data.get(`${prefix}passwordConfirm`) ?? '')
}

/** Mismas reglas y mensajes que las anotaciones de `RegisterRequest`, más la confirmación (solo cliente). */
export function validateAccount(account: CustomerRegistration, passwordConfirm: string): FieldErrors {
  const passwordError = newPasswordError(account.password)
  return compact({
    name: requiredText(account.name, 'El nombre', 100),
    surname: requiredText(account.surname, 'El apellido', 100),
    identification: requiredText(account.identification, 'La identificación', 20),
    dateBirth: birthDateError(account.dateBirth),
    numberPhone: requiredText(account.numberPhone, 'El teléfono', 10),
    email: emailError(account.email),
    password: passwordError,
    passwordConfirm: !passwordConfirm
      ? 'Confirma tu contraseña.'
      : !passwordError && passwordConfirm !== account.password
        ? 'Las contraseñas no coinciden.'
        : undefined,
  })
}

/** Enfoca el primer campo con error, en el orden del formulario. */
export function focusFirstError(order: readonly string[], errors: Record<string, string>, idFor: (field: string) => string) {
  const first = order.find((field) => errors[field])
  if (first) document.getElementById(idFor(first))?.focus()
}
