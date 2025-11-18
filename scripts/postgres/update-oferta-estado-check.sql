-- Adds the new negotiation states that the service now relies on
ALTER TABLE public.ofertas DROP CONSTRAINT IF EXISTS ofertas_estado_check;
ALTER TABLE public.ofertas
    ADD CONSTRAINT ofertas_estado_check CHECK (estado IN (
        'EN_NEGOCIACION',
        'PENDIENTE_DE_PAGO',
        'ACEPTADA',
        'ASIGNADO',
        'RECHAZADA',
        'CANCELADA'
    ));
