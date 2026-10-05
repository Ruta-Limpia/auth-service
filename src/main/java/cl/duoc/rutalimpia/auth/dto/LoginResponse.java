package cl.duoc.rutalimpia.auth.dto;

// expiraEn va en minutos, igual que JWT_EXPIRATION_MINUTES
public record LoginResponse(
        String token,
        String tipo,
        long expiraEn,
        UsuarioResponse usuario) {

    public static LoginResponse bearer(String token, long expiraEn, UsuarioResponse usuario) {
        return new LoginResponse(token, "Bearer", expiraEn, usuario);
    }

    @Override
    public String toString() {
        return "LoginResponse[token=****, tipo=" + tipo + ", expiraEn=" + expiraEn + ", usuario=" + usuario + "]";
    }
}
