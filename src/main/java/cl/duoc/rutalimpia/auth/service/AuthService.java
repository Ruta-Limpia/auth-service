package cl.duoc.rutalimpia.auth.service;

import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.LoginResponse;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;

public interface AuthService {

    UsuarioResponse registrar(RegistroRequest request);

    LoginResponse login(LoginRequest request);
}
