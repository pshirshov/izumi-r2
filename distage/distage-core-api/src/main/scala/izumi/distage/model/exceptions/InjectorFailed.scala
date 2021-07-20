package izumi.distage.model.exceptions

import izumi.distage.model.definition.errors.DIError

class InjectorFailed(message: String, val errors: List[DIError]) extends DIException(message)
