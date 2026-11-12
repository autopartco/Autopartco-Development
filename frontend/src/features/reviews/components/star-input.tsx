import { StarIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

import { RATING_WORDS, ratingWord } from '../lib/rating'

interface StarInputProps {
  /** Nombre del grupo de radios; debe ser único en la página. */
  name: string
  /** 0 = sin elegir. */
  value: number
  onChange: (value: number) => void
  /** Pregunta que nombra el grupo para los lectores de pantalla. */
  legend: string
  size?: 'lg' | 'sm'
  /** Texto del puntaje elegido junto a las estrellas (solo el tamaño grande). */
  showWord?: boolean
  disabled?: boolean
  describedBy?: string
}

/**
 * Estrellas para calificar: un grupo de radios nativos, así que Tab entra al grupo y las flechas cambian el
 * puntaje. Cada estrella se lee «4 de 5: Bueno»; el foco se dibuja alrededor de la estrella activa.
 */
export function StarInput({ name, value, onChange, legend, size = 'lg', showWord, disabled, describedBy }: StarInputProps) {
  const large = size === 'lg'
  return (
    <fieldset className="flex min-w-0 flex-col gap-1" aria-describedby={describedBy} disabled={disabled}>
      <legend className="sr-only">{legend}</legend>
      <div className="flex items-center gap-x-3">
        <div className="flex items-center">
          {RATING_WORDS.map((word, index) => {
            const star = index + 1
            const filled = value >= star
            return (
              <label
                key={word}
                className={cn(
                  'relative grid size-11 cursor-pointer place-items-center rounded-md transition-colors hover:bg-ap-paper has-focus-visible:outline-2 has-focus-visible:outline-offset-0 has-focus-visible:outline-ap-red',
                  disabled && 'cursor-not-allowed opacity-60',
                )}
              >
                <input
                  type="radio"
                  name={name}
                  value={star}
                  checked={value === star}
                  onChange={() => onChange(star)}
                  className="peer sr-only"
                />
                <span className="sr-only">{`${star} de 5: ${word}`}</span>
                <StarIcon
                  aria-hidden
                  className={cn(large ? 'size-8' : 'size-6', filled ? 'fill-ap-graphite text-ap-graphite' : 'text-ap-line-strong')}
                />
              </label>
            )
          })}
        </div>
        {large && showWord ? (
          <p className="min-w-0 text-base font-semibold text-ap-ink" aria-live="polite">
            {value > 0 ? ratingWord(value) : <span className="font-normal text-ap-muted">Toca una estrella</span>}
          </p>
        ) : null}
      </div>
    </fieldset>
  )
}
