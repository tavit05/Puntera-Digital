package com.punteradigital.inventory.domain.usecase

import javax.inject.Inject

class QcValidationUseCase @Inject constructor() {

    // Simulación del Checklist de Calidad
    data class QcChecklist(
        val hasGoodSeams: Boolean, // Costuras
        val hasGoodSole: Boolean,  // Suela
        val hasSteelToe: Boolean,  // Puntera de Acero
        val hasGoodFinish: Boolean // Acabado
    )

    /**
     * Valida el checklist. Si una sola falla, la bota es rechazada.
     */
    fun validateBoot(checklist: QcChecklist): Boolean {
        return checklist.hasGoodSeams && 
               checklist.hasGoodSole && 
               checklist.hasSteelToe && 
               checklist.hasGoodFinish
    }
}
