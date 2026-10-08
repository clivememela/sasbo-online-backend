import { useEffect, useState } from 'react'

import { memberService } from '@/services/memberService.js'
import { emailingFrom } from '@/components/members/welcomeLetterOutcome.js'

/**
 * Whether this server emails a captured member their welcome letter: `'on'`, `'off'`, or `'unknown'` (A64).
 *
 * Asked once, when the capture screen mounts its dialog, so the answer is in long before a form is filled and Save is
 * pressed. It only changes what the dialog SAYS: whether a letter goes out is decided by the server on the capture
 * itself, so a slow or failed answer here can never change what a capture does. Failure reads as `'unknown'`.
 */
export default function useWelcomeLetterEmail() {
  const [emailing, setEmailing] = useState('unknown')

  useEffect(() => {
    let current = true
    memberService.getWelcomeLetterMetadata()
      .then((metadata) => {
        if (current) setEmailing(emailingFrom(metadata))
      })
      .catch(() => {
        if (current) setEmailing('unknown')
      })
    return () => {
      current = false
    }
  }, [])

  return emailing
}
