import { PasswordField, TextField } from '@/components/common/form-fields'
import { PASSWORD_HINT, type FieldErrors } from '../lib/validation'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

interface AccountFieldsProps {
  /** Prefijo de `id` y `name` (p. ej. `cuenta-`), para no chocar con otros campos del formulario. */
  prefix: string
  errors: FieldErrors
  emailLabel?: string
}

/** Datos personales y de acceso. Dos columnas desde sm. */
export function AccountFields({ prefix, errors, emailLabel = 'Correo' }: AccountFieldsProps) {
  const id = (field: string) => `${prefix}${field}`
  return (
    <div className="grid gap-5 sm:grid-cols-2">
      <TextField id={id('name')} name={id('name')} label="Nombre" autoComplete="given-name" maxLength={100} required error={errors.name} />
      <TextField
        id={id('surname')}
        name={id('surname')}
        label="Apellido"
        autoComplete="family-name"
        maxLength={100}
        required
        error={errors.surname}
      />
      <TextField
        id={id('identification')}
        name={id('identification')}
        label="Número de identificación"
        hint="Cédula de ciudadanía o de extranjería, sin puntos."
        autoComplete="off"
        maxLength={20}
        required
        error={errors.identification}
      />
      <TextField
        id={id('dateBirth')}
        name={id('dateBirth')}
        type="date"
        label="Fecha de nacimiento"
        autoComplete="bday"
        max={today()}
        required
        error={errors.dateBirth}
      />
      <TextField
        id={id('numberPhone')}
        name={id('numberPhone')}
        type="tel"
        label="Celular"
        hint="10 dígitos, sin +57."
        autoComplete="tel-national"
        inputMode="tel"
        maxLength={10}
        required
        error={errors.numberPhone}
      />
      <TextField
        id={id('email')}
        name={id('email')}
        type="email"
        label={emailLabel}
        autoComplete="email"
        inputMode="email"
        maxLength={100}
        required
        error={errors.email}
      />
      <PasswordField
        id={id('password')}
        name={id('password')}
        label="Contraseña"
        hint={PASSWORD_HINT}
        autoComplete="new-password"
        maxLength={72}
        required
        error={errors.password}
      />
      <PasswordField
        id={id('passwordConfirm')}
        name={id('passwordConfirm')}
        label="Confirmar contraseña"
        autoComplete="new-password"
        maxLength={72}
        required
        error={errors.passwordConfirm}
      />
    </div>
  )
}
