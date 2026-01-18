package com.hackanddrink.Hack.DrinkManager;

import java.security.Timestamp;
import java.sql.*;
import javax.sql.DataSource;
import java.util.UUID; // Para generar el localizador aleatorio

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

@SessionAttributes({"nombreMapa", "detallesMapa", "posicionMapa",
        "descripcionPermisos", "tipoPermisos",
        "nombrePersonal", "correoPersonal"})

@Controller
public class SistemaController {

    private final DataSource dataSource;

    public SistemaController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    //Este método captura AUTOMÁTICAMENTE cualquier SQLException lanzada por los métodos de abajo, me permite
    //obviar el try catch en estos porque si no tendríamos que poner la captura de la excepción cada vez
    //que trabajemos con la base de datos y es un pelín peñazo. Utiliza SQLException exactamente igual que con los
    //try-catch
    @ExceptionHandler(SQLException.class)
    @ResponseBody
    public String manejarErroresSQL(SQLException e) {
        return "ERROR EN LA BASE DE DATOS: " + e.getMessage() + " (Código: " + e.getErrorCode() + ")";
    }

    //A PARTIR DE AQUÍ COMIENZAN LA IMPLEMENTACIÓN DE LOS MÉTODOS REFERENTES A LAS OPERACIONES DEL SISTEMA

    //----OPERACIONES DE SERVICIOS----

    @GetMapping("/admin/servicios")
    public String opcionesServicios(){
        return "servicios-opciones";
    }

    @GetMapping("/admin/servicios/proveedores")
    public String asignarProveedores() {
        return "gestionar-proveedores";
    }

    //Esta es la función que asigna un proveedor a un servicio
    //Dado que tenía que enviar un listado, he decidio que maximo haya 10 entradas
    //en el HTML y que además se use un DTO (Una clase para recoger los datos del formulario)
    //porque no tenía muchas más opciones sin javascript y quería que tampoco fuera poner 800 paramrequest
    //el DTO ha sido declarado un poco más abajo, sé no es muy ortodoxo pero es para tenerlo todo en
    //un mismo archivo y que sea más fácil de ver, auqne también podría ser más lioso de ver con demasiado código
    //si crees que faltan muchas comprobaciones, como tamaños maximos... Esto es porque las contemplo en el html
    //de forma que el html no permite enviar datos que no cumplan esas restricciones
    //soy consciente de que el html puede ser modificado por el usuario, pero para este caso de uso
    //no me preocupa demasiado, ya que es un sistema interno y no público y además las comprobaciones más importantes
    //se hacen en la base de datos (como que no existan duplicados, que los datos existan previamente, etc)
    @PostMapping("/admin/servicios/proveedores-asignar" )
    public String asignarProveedores(@ModelAttribute FormularioAsignacionDTO formulario, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); //la lógica transaccional es manejada por mí mismo
            //asegurandome de que no se producirán commits automáticos

            String sql = "SELECT COUNT(*) FROM PROVEEDORES WHERE NIF = ? ";
            try (PreparedStatement ps = conn.prepareStatement(sql)){

                ps.setString(1, formulario.getNifEmpresa());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        model.addAttribute("mensaje", "El NIF de la empresa no existe en la base de datos.");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            sql = "SELECT COUNT(*) FROM SERVICIOS WHERE NOMBRE = ? ";
            try(PreparedStatement ps = conn.prepareStatement(sql)){

                ps.setString(1, formulario.getNomServicio());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        model.addAttribute("mensaje", "El nombre del servicio no existe en la base de datos.");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            //lo he copiado de sqldeveloper y se ha puesto solo así, con los espacios y los saltos de linea
            //mejor así que todo en una sola linea.
            sql = "DECLARE\n" +
                    "\n" +
                    "nombre_relacion VARCHAR2(20);\n" +
                    "area_relacion NUMBER;\n" +
                    "conteo NUMBER;\n" +
                    "\n" +
                    "BEGIN\n" +
                    "\n" +
                    "nombre_relacion := ?;\n" +
                    "area_relacion := ?;\n" +
                    "\n" +
                    "SELECT COUNT(*) INTO conteo FROM Asignacion WHERE Nombre = nombre_relacion AND Area = area_relacion;\n" +
                    "\n" +
                    "? := conteo;\n" +
                    "\n" +
                    "IF conteo = 0 THEN\n" +
                    "INSERT INTO Asignacion VALUES ( nombre_relacion, area_relacion, ?, ?);\n" +
                    "END IF;\n" +
                    "\n" +
                    "END; ";

            try (CallableStatement ps = conn.prepareCall(sql)){

                ps.setString(1, formulario.getNomServicio());
                ps.setInt(2, formulario.getArea());
                ps.registerOutParameter(3, Types.INTEGER); // Parámetro de salida para el conteo
                ps.setString(4, formulario.getNifEmpresa());
                ps.setInt(5, formulario.getNumEmpleados());

                ps.execute();

                int count = ps.getInt(3); // Obtener el valor del conteo

                if (count > 0) {
                    conn.rollback();
                    model.addAttribute("mensaje", "Ya existe una asignación para este servicio en el área especificada.");
                    model.addAttribute("exito", false);
                    return "resultado-servicios";
                }
                if (formulario.getNumEmpleados() == 0){
                    conn.commit();
                } else{
                    int numEmpleados = formulario.getNumEmpleados() != null ? formulario.getNumEmpleados() : 0;

                    if (numEmpleados > 0 && formulario.getEmpleados() != null) {
                        //Si hay empleados, entramos en el bucle
                        //Saco un parametro para ver si el empleado ya existe
                        String sqlEmp = "DECLARE\n" +
                                "\n" +
                                "dni_relacion VARCHAR2(20);\n" +
                                "conteo NUMBER;\n" +
                                "conteo2 NUMBER;\n" +
                                "BEGIN\n" +
                                "\n" +
                                "dni_relacion := ?;\n" +
                                "\n" +
                                "SELECT COUNT(*) INTO conteo2 FROM Empleados WHERE DNI = dni_relacion;\n" +
                                "SELECT COUNT(*) INTO conteo  FROM Trabajo WHERE DNI = dni_relacion;\n" +
                                "\n" +
                                "? := conteo2;\n" +
                                "? := conteo;\n" +
                                "\n" +
                                "IF conteo = 0 AND conteo2 = 0 THEN\n" +
                                "INSERT INTO Empleados VALUES (dni_relacion, ?, ?, ?);\n" +
                                "INSERT INTO Trabajo VALUES ( dni_relacion, ?, ?);\n" +
                                "END IF;\n" +
                                "\n" +
                                "END;";

                        try (CallableStatement psEmp = conn.prepareCall(sqlEmp)) {
                            //Contador para asegurarnos de no procesar más de los indicados
                            int procesados = 0;

                            for (EmpleadoInputDTO empleado : formulario.getEmpleados()) {

                                //esto lo que hace es evitar que procese empleados vacios o nulos
                                if (empleado.getDni() == null || empleado.getDni().trim().isEmpty()) {
                                    continue; //Saltamos esta iteración si el DNI está vacío
                                }

                                // Si ya hemos procesado el número indicado de empleados, paramos
                                if (procesados >= numEmpleados) break;

                                psEmp.setString(1, empleado.getDni());
                                psEmp.registerOutParameter(2, Types.INTEGER); // conteo2(Existe empleado)
                                psEmp.registerOutParameter(3, Types.INTEGER); // conteo(Existe trabajo)
                                psEmp.setString(4, empleado.getNombre());
                                psEmp.setString(5, empleado.getApellidos());
                                psEmp.setString(6, empleado.getTelefono());
                                psEmp.setString(7, formulario.getNomServicio());
                                psEmp.setInt(8, formulario.getArea());

                                psEmp.execute();

                                int countEmp = psEmp.getInt(2);
                                int countTrabajo = psEmp.getInt(3);

                                if (countEmp > 0 || countTrabajo > 0) {
                                    conn.rollback(); //Importante hacer rollback si falla uno para que los demás no se queden guardados
                                    //recordemos es una transacción, aunque si directamente lanzamos un return se supone que no se hace commit
                                    model.addAttribute("mensaje", "El empleado con DNI " + empleado.getDni() + " ya existe en la base de datos o ya se le ha asignado un trabajo.");
                                    model.addAttribute("exito", false);
                                    return "resultado-servicios";
                                }
                                procesados++;
                            }
                            //Si el bucle termina sin errores
                            conn.commit();

                        }
                    }
                }
            }
        }

        model.addAttribute("mensaje", "Operación exitosa.");
        model.addAttribute("exito", true);
        return "resultado-servicios";
    }

    //este es el dto que usaré para recoger los datos del formulario de asignación de proveedores
    //este a su vez utiliza el dto de empleado que está más abajo para contener todos los datos
    //en una lista de tipo EmpleadoInputDTO
    public static class FormularioAsignacionDTO {
        private String nifEmpresa;
        private String nomServicio;
        private Integer numEmpleados;
        private Integer area;
        private java.util.List<EmpleadoInputDTO> empleados = new java.util.ArrayList<>();

        public String getNifEmpresa() {
            return nifEmpresa;
        }

        public void setNifEmpresa(String nifEmpresa) {
            this.nifEmpresa = nifEmpresa;
        }

        public String getNomServicio() {
            return nomServicio;
        }

        public void setNomServicio(String nomServicio) {
            this.nomServicio = nomServicio;
        }

        public Integer getNumEmpleados() {
            return numEmpleados;
        }

        public void setNumEmpleados(Integer numEmpleados) {
            this.numEmpleados = numEmpleados;
        }

        public java.util.List<EmpleadoInputDTO> getEmpleados() {
            return empleados;
        }

        public void setEmpleados(java.util.List<EmpleadoInputDTO> empleados) {
            this.empleados = empleados;
        }

        public Integer getArea() {
            return area;
        }

        public void setArea(Integer area) {
            this.area = area;
        }
    }

    //este es el dto que usaré para recoger los datos de cada empleado
    public static class EmpleadoInputDTO {
        private String nombre;
        private String apellidos;
        private String telefono;
        private String dni;

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public String getApellidos() {
            return apellidos;
        }

        public void setApellidos(String apellidos) {
            this.apellidos = apellidos;
        }

        public String getTelefono() {
            return telefono;
        }

        public void setTelefono(String telefono) {
            this.telefono = telefono;
        }

        public String getDni() {
            return dni;
        }

        public void setDni(String dni) {
            this.dni = dni;
        }
    }

    @GetMapping("/admin/servicios/turnos")
    public String asignarTurnos(){
        return "gestionar-turnos";
    }


    //los parámetros de fecha y hora se envían en formato ISO 8601 (YYYY-MM-DDTHH:MM:SS), es decir, en datime-local
    //Los capturamos omo LocalDateTime y luego los convertimos a Timestamp para pasarlos a Oracle, en Oracle uso DATE
    //porque maneja fecha y hora perfectamente con ese tipo de dato, timestamp es solaemnte para fracciones de segundo
    @PostMapping("/admin/servicios/turnos-crear")
    public String asignarTurnos(@RequestParam("dni") String dni, @RequestParam("area") int area,
                                @RequestParam("fechaInicio") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime fechaInicio,
                                @RequestParam("fechaSalida") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime fechaSalida,
                                Model model) throws SQLException{

        if (fechaInicio.isAfter(fechaSalida) || fechaInicio.isEqual(fechaSalida)) {
            model.addAttribute("mensaje", "Error: La fecha de inicio debe ser anterior a la de salida.");
            model.addAttribute("exito", false);
            return "resultado-servicios";
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            //copiado y pegado desde sqldeveloper, por eso mantiene formato
            String sql = "DECLARE\n" +
                    "\n" +
                    "inicio DATE;\n" +
                    "salida DATE;\n" +
                    "v_dni VARCHAR2(9);\n" +
                    "area NUMBER;\n" +
                    "contador NUMBER;\n" +
                    "\n" +
                    "BEGIN\n" +
                    "\n" +
                    "inicio := ?;\n" +
                    "salida := ?;\n" +
                    "v_dni := ?;\n" +
                    "area := ?;\n" +
                    "\n" +
                    "SELECT COUNT(*) INTO contador FROM TURNOS WHERE DNI = v_dni AND (FechaInicio < salida AND FechaSalida > inicio);\n" +
                    "\n" +
                    "? := contador;\n" +
                    "\n" +
                    "IF contador = 0 THEN\n" +
                    "\n" +
                    "    INSERT INTO TURNOS VALUES (v_dni, inicio, salida, area);\n" +
                    "\n" +
                    "END IF;\n" +
                    "\n" +
                    "END;";

            try (CallableStatement cs = conn.prepareCall(sql)) {

                //Me ha estado dando errores si lo dejaba así  así que mejor convertirlo de
                //LocalDateTime a java.sql.Timestamp, que Oracle mapea a DATE/TIMESTAMP
                cs.setTimestamp(1, java.sql.Timestamp.valueOf(fechaInicio));
                cs.setTimestamp(2, java.sql.Timestamp.valueOf(fechaSalida));
                cs.setString(3, dni);
                cs.setInt(4, area);

                //Registramos el parámetro de salida para capturar el valor de 'contador'
                //que es el que nos permite saber si hay solapamientos para poder indicar al
                //usuario el tipo de error
                cs.registerOutParameter(5, Types.INTEGER);

                cs.execute();

                int solapamientos = cs.getInt(5);

                if (solapamientos > 0) {
                    conn.rollback(); //volvemos porque no se ha podido finalizar
                    model.addAttribute("mensaje", "Error: Se ha producido un solapamiento con la fecha de otro turno.");
                    model.addAttribute("exito", false);
                    return "resultado-servicios";
                } else {
                    conn.commit(); //commit exitoso
                    model.addAttribute("mensaje", "Turno creado correctamente para el DNI: " + dni);
                    model.addAttribute("exito", true);
                    return "resultado-servicios";
                }
            }

        }
    }


    @GetMapping("/admin/servicios/suministros")
    public String gestionarSuministros(){
        return "gestionar-suministros";
    }

    @PostMapping("/admin/servicios/suministros-reabastecer")
    public String reabastecerSuministros(@RequestParam("idSuministro") String idSuministro,
                                         @RequestParam("area") int area,
                                         @RequestParam("nivel") int nivel,
                                         Model model) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "UPDATE SUMINISTROS SET Nivel = Nivel + ? WHERE Identificador = ? AND Area = ? ";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, nivel);
                ps.setString(2, idSuministro);
                ps.setInt(3, area);

                int filasActualizadas = ps.executeUpdate();

                if (filasActualizadas > 0) {

                    String sqlSelect = "SELECT Nivel FROM SUMINISTROS WHERE Identificador = ? AND Area = ?";
                    try (PreparedStatement psSelect = conn.prepareStatement(sqlSelect)) {
                        psSelect.setString(1, idSuministro);
                        psSelect.setInt(2, area);

                        ResultSet rs = psSelect.executeQuery();
                        int nuevoNivelTotal = 0;
                        if(rs.next()){
                            nuevoNivelTotal = rs.getInt(1);
                        }

                        conn.commit();

                        model.addAttribute("mensaje", "Reabastecimiento exitoso");
                        model.addAttribute("idSuministro", idSuministro);
                        model.addAttribute("area", area);
                        model.addAttribute("nivelTotal", nuevoNivelTotal);
                        model.addAttribute("exito", true);

                        return "resultados-suministros";
                    }
                } else {
                    conn.rollback();
                    model.addAttribute("mensaje", "Error: No se encontró el suministro con el ID y área especificados.");
                    model.addAttribute("exito", false);
                    return "resultados-suministros";
                }

            }
        }
    }

    @GetMapping("/admin/servicios/incidencia")
    public String reportarIncidencia(){
        return "gestionar-incidencia";
    }

    @PostMapping("/admin/servicios/incidencia-crear")
    public String reportarIncidencia(@RequestParam("dni") String dni,
                                     @RequestParam("comentario") String comentario,
                                     Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "SELECT COUNT(*) FROM CLIENTE WHERE DNI = ?";

            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        //El DNI no existe en la tabla CLIENTES
                        conn.rollback();
                        model.addAttribute("mensaje", "No existe cliente con ese DNI registrado");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            //genero un identificador aleatorio para la incidencia
            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 9; i++) {
                sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
            }
            String identificador = sb.toString();

            //ejemplo de como las restricciones mas basicas de longitudes etc se pueden gestionar
            //para simplificar en el controlador no las he puesto todas, la mayoria esta en html
            if (comentario.length() > 200) {
                model.addAttribute("mensaje", "Comentario demasiado largo, máximo 200 caracteres.");
                model.addAttribute("exito", false);
                return "resultado-servicios";
            }
            sql = "INSERT INTO INCIDENCIA VALUES (?, ?, ?)";

            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ps.setString(2, identificador);
                ps.setString(3, comentario);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Incidencia reportada correctamente con ID: " + identificador);
                model.addAttribute("exito", true);
                return "resultado-servicios";
            }

        }

    }

    @GetMapping("/admin/servicios/valoracion")
    public String enviarValoracion(){
        return "gestionar-valoracion";
    }

    @PostMapping("/admin/servicios/valoracion-enviar")
    public String procesarValoracion(@RequestParam("dni") String dni,
                                     @RequestParam("localizador") String localizador,
                                     @RequestParam("valoracion") int valoracion,
                                     @RequestParam("comentario") String comentario,
                                     Model model) throws SQLException {

        //quiero comprobar que no se mete valoración negativa o mayor que 5
        //quiero comprobar que la longitud de la descripción no es excesiva
        if (valoracion < 0 || valoracion > 5) {
            model.addAttribute("mensaje", "Error: La valoración debe estar entre 0 y 5.");
            model.addAttribute("exito", false);
            return "resultado-servicios";
        }

        if (comentario.length() > 200){
            model.addAttribute("mensaje", "Error: El comentario es demasiado largo (máximo 300 caracteres).");
            model.addAttribute("exito", false);
            return "resultado-servicios";
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);


            String sql = "SELECT COUNT(*) FROM ENTRADA WHERE DNI = ? AND Localizador = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ps.setString(2, localizador);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int existe = rs.getInt(1);
                    if (existe == 0) {
                        conn.rollback();
                        model.addAttribute("mensaje", "Error: No se encontró una entrada válida para ese DNI y Localizador.");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 9; i++) {
                sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
            }
            String identificador = sb.toString();

            sql = "INSERT INTO VALORACION (DNI, Localizador, Identificador, Comentario, Valoracion) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ps.setString(2, localizador);
                ps.setString(3, identificador);
                ps.setString(4, comentario);
                ps.setInt(5, valoracion);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Valoración enviada correctamente. ID Referencia: " + identificador);
                model.addAttribute("exito", true);
                return "resultado-servicios";
            }
        }
    }


    //----FIN DE LAS OPERACIONES DE SERVICIOS----


    //----INICIO GESTION DE ARTISTAS----//

     //mostramos el formulario del menu de gestion de artistas
    @GetMapping("/admin/artista/menu")
    public String mostrarMenuArtistas() {

        return "menuArtistas";

    }



    //mostramos el formulario de dar de alta
    @GetMapping("/admin/artista/alta")
    public String mostrarFormulario() {

        return "formularioArtista";

    }

    //post para guardar el artista nuevo
    @PostMapping("/admin/artista/guardar")
    public String guardarArtista(

            @RequestParam String nombre,
            @RequestParam String apellidos,
            @RequestParam String dni,
            @RequestParam String correo,
            @RequestParam String telefono,
            @RequestParam String direccion,
            @RequestParam int cache,
            @RequestParam String genero,

            Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            //aqui validamos la restriccion semantica 2.1 y 2.2
            String sqlCheck = "SELECT COUNT(*) FROM ARTISTAS WHERE DNI_NIF = ? OR CORREO_ELECTRONICO = ?";

            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheck)) {

                psCheck.setString(1, dni);
                psCheck.setString(2, correo);
                ResultSet rs = psCheck.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    model.addAttribute("mensaje", "Ya existe un artista con ese DNI o correo electrónico.");
                    return "resultadoArtista";
                }

            }

            //hacemos la insercion de el artista
            String sqlInsert = "INSERT INTO ARTISTAS (NOMBRE, APELLIDOS, DNI_NIF, CORREO_ELECTRONICO, TELEFONO, DIRECCION, CACHE, GENERO_MUSICAL) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

                ps.setString(1, nombre);
                ps.setString(2, apellidos);
                ps.setString(3, dni);
                ps.setString(4, correo);
                ps.setString(5, telefono);
                ps.setString(6, direccion);
                ps.setInt(7, cache);
                ps.setString(8, genero);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Artista " + nombre + " registrado con éxito.");
                return "resultadoArtista";

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
        }
    }


    //con esto mostramosel formulario de buscar un artista para editar identificandolo con su DNI
    @GetMapping("/admin/artista/modificar")
    public String mostrarBuscadorModificar() {

        return "buscarArtistaParaEditar";

    }


    //con este post lo que hacemos es cargar los datos del artista relacionados con el DNI que hemos buscado para realizar la modificacion de lo que queramos
    @PostMapping("/admin/artista/cargar-datos")
    public String cargarDatosParaEdicion(@RequestParam String dni, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {

            String sql = "SELECT * FROM ARTISTAS WHERE DNI_NIF = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, dni);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {

                    model.addAttribute("dni", rs.getString("DNI_NIF"));
                    model.addAttribute("nombre", rs.getString("NOMBRE"));
                    model.addAttribute("apellidos", rs.getString("APELLIDOS"));
                    model.addAttribute("correo", rs.getString("CORREO_ELECTRONICO"));
                    model.addAttribute("telefono", rs.getString("TELEFONO"));
                    model.addAttribute("direccion", rs.getString("DIRECCION"));
                    model.addAttribute("cache", rs.getInt("CACHE"));
                    model.addAttribute("genero", rs.getString("GENERO_MUSICAL"));

                    return "modificarArtista"; //el formulario con los datos rellenos

                } else {
                    //caso de que el DNI que hemos buscado no existe
                    model.addAttribute("mensaje", "No existe ningún artista con el DNI: " + dni);
                    return "resultadoArtista";
                }
            }
        }
    }

    //cogemos los datos con las modificaciones y actualizamos el artista
    @PostMapping("/admin/artista/actualizar")
    public String procesarActualizacion(

            @RequestParam String dni,
            @RequestParam String nombre,
            @RequestParam String apellidos,
            @RequestParam String correo,
            @RequestParam String telefono,
            @RequestParam String direccion,
            @RequestParam int cache,
            @RequestParam String genero,

            Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            //validamos que el correo que se ha introducido ahora no este asociado a otro artista existente
            String sqlCheckCorreo = "SELECT COUNT(*) FROM ARTISTAS WHERE CORREO_ELECTRONICO = ? AND DNI_NIF != ?";

            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheckCorreo)) {

                psCheck.setString(1, correo);
                psCheck.setString(2, dni);
                ResultSet rs = psCheck.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    //sacamos el error
                    model.addAttribute("mensaje", "El correo electrónico ya está en uso por otro artista.");
                    return "resultadoArtista";

                }
            }


            String sqlUpdate = "UPDATE ARTISTAS SET NOMBRE = ?, APELLIDOS = ?, CORREO_ELECTRONICO = ?, " +
                    "TELEFONO = ?, DIRECCION = ?, CACHE = ?, GENERO_MUSICAL = ? WHERE DNI_NIF = ?";

            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {

                ps.setString(1, nombre);
                ps.setString(2, apellidos);
                ps.setString(3, correo);
                ps.setString(4, telefono);
                ps.setString(5, direccion);
                ps.setInt(6, cache);
                ps.setString(7, genero);
                ps.setString(8, dni);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Datos del artista actualizados correctamente.");
                return "resultadoArtista";

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
        }
    }


    //formulario dar de baja
    @GetMapping("/admin/artista/baja")
    public String mostrarFormularioBaja() {

        return "bajaArtista";

    }

    //eliminamos el artista asociado a ese dni y correo pero antes comprobamos ciertas cosas
    @PostMapping("/admin/artista/eliminar")
    public String procesarBaja(@RequestParam String dni, @RequestParam String correo, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            //comprobamos si tiene asociadas actuaciones ya que en ese caso no se puede eliminar el artista
            String sqlCheck = "SELECT COUNT(*) FROM Asociado WHERE DNI_NIF = ? AND HoraInicio2 >= CURRENT_TIMESTAMP";

            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheck)) {

                psCheck.setString(1, dni);
                ResultSet rs = psCheck.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {

                    model.addAttribute("mensaje", "No se puede dar de baja: El artista tiene actuaciones pendientes en la tabla Asociado.");
                    return "resultadoArtista";

                }
            }

            try {

                //borramos incluido dependencias en asociado, ocupa y firma
                String[] tablasDependientes = {"Ocupa", "Asociado", "Firma"};
                for (String tabla : tablasDependientes) {

                    try (PreparedStatement psDep = conn.prepareStatement("DELETE FROM " + tabla + " WHERE DNI_NIF = ?")) {

                        psDep.setString(1, dni);
                        psDep.executeUpdate();

                    }
                }

                //borramos el artista
                String sqlDeleteArtista = "DELETE FROM ARTISTAS WHERE DNI_NIF = ? AND CORREO_ELECTRONICO = ?";

                try (PreparedStatement psDelete = conn.prepareStatement(sqlDeleteArtista)) {

                    psDelete.setString(1, dni);
                    psDelete.setString(2, correo);

                    int filas = psDelete.executeUpdate();

                    if (filas > 0) {

                        conn.commit();
                        model.addAttribute("mensaje", "Artista eliminado con éxito.");

                    } else {

                        conn.rollback();
                        model.addAttribute("mensaje", "Los datos de DNI y Correo no coinciden.");

                    }
                }

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
            return "resultadoArtista";
        }
    }


    //formulario pa el registro de actuaciones
    @GetMapping("/admin/artista/actuacion")
    public String mostrarFormularioActuacion() {

        return "formularioActuacion";

    }

    //post guardamos la actuacion
    @PostMapping("/admin/artista/guardarActuacion")
    public String guardarActuacion(

            @RequestParam String dni,
            @RequestParam String fecha,
            @RequestParam String horaInicio,
            @RequestParam String horaFin,
            @RequestParam int escenario,
            Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            //fechas inicio y fin
            String inicioCompleto = fecha + " " + horaInicio + ":00";
            String finCompleto = fecha + " " + horaFin + ":00";

            //comprobamos que el escenario exista
            String sqlExisteEscenario = "SELECT COUNT(*) FROM Escenarios WHERE NumeroEscenario = ?";
            try (PreparedStatement psEsc = conn.prepareStatement(sqlExisteEscenario)) {

                psEsc.setInt(1, escenario);
                ResultSet rsEsc = psEsc.executeQuery();

                if (rsEsc.next() && rsEsc.getInt(1) == 0) {

                    model.addAttribute("mensaje", "El escenario número " + escenario + " no existe.");
                    return "resultadoArtista";

                }
            }

            //comprobamos que ese escenario no este ocupado en ese rango de horas para eviar solapamientos
            String sqlCheckConflict = "SELECT COUNT(*) FROM Asociado " + "WHERE NumeroEscenario = ? " + "AND ((HoraInicio2 <= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') AND HoraFin2 > TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')) " +
                    "OR (HoraInicio2 < TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') AND HoraFin2 >= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')))";

            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheckConflict)) {

                psCheck.setInt(1, escenario);
                psCheck.setString(2, inicioCompleto);
                psCheck.setString(3, inicioCompleto);
                psCheck.setString(4, finCompleto);
                psCheck.setString(5, finCompleto);

                ResultSet rs = psCheck.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {

                    model.addAttribute("mensaje", "El escenario ya tiene una actuación en esa franja horaria.");
                    return "resultadoArtista";

                }
            }

            //inserccion
            String sqlInsert = "INSERT INTO Asociado (DNI_NIF, HoraInicio2, HoraFin2, NumeroEscenario) " + "VALUES (?, TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS'), TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS'), ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

                ps.setString(1, dni);
                ps.setString(2, inicioCompleto);
                ps.setString(3, finCompleto);
                ps.setInt(4, escenario);

                ps.executeUpdate();
                conn.commit(); // Confirmamos los cambios en la BD

                model.addAttribute("mensaje", "Actuación registrada con éxito en el escenario " + escenario);
                return "resultadoArtista";

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
        }
    }

    //asignacion camerinos parecido a escenarios
    @GetMapping("/admin/artista/camerino")
    public String mostrarFormularioCamerino() {

        return "formularioCamerino";

    }

    @PostMapping("/admin/artista/asignarCamerino")
    public String asignarCamerino(

            @RequestParam String dni,
            @RequestParam String fecha,
            @RequestParam String horaInicio,
            @RequestParam String horaFin,
            @RequestParam int idCamerino,
            Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            String inicioCompleto = fecha + " " + horaInicio + ":00";
            String finCompleto = fecha + " " + horaFin + ":00";

            //comprobamos que el artista exista
            String sqlExisteArt = "SELECT COUNT(*) FROM ARTISTAS WHERE DNI_NIF = ?";

            try (PreparedStatement ps = conn.prepareStatement(sqlExisteArt)) {

                ps.setString(1, dni);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) == 0) {

                    model.addAttribute("mensaje", "El artista no existe.");
                    return "resultadoArtista";

                }
            }

            //comprobamos tb el camerino
            String sqlExisteCam = "SELECT COUNT(*) FROM Camerinos WHERE Identificacion = ?";

            try (PreparedStatement ps = conn.prepareStatement(sqlExisteCam)) {

                ps.setInt(1, idCamerino);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) == 0) {

                    model.addAttribute("mensaje", "El camerino " + idCamerino + " no existe.");
                    return "resultadoArtista";

                }
            }

            //y aqui tb evitamos solapamientos en los camerinos
            String sqlCheckConflict = "SELECT COUNT(*) FROM Ocupa " + "WHERE Identificacion = ? " + "AND ((HoraInicio <= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') AND HoraFin > TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')) " +
                    "OR (HoraInicio < TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') AND HoraFin >= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')))";

            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheckConflict)) {

                psCheck.setInt(1, idCamerino);
                psCheck.setString(2, inicioCompleto);
                psCheck.setString(3, inicioCompleto);
                psCheck.setString(4, finCompleto);
                psCheck.setString(5, finCompleto);

                ResultSet rs = psCheck.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {

                    model.addAttribute("mensaje", "El camerino " + idCamerino + " ya está ocupado en ese horario.");
                    return "resultadoArtista";

                }
            }

            //insercciom
            String sqlInsert = "INSERT INTO Ocupa (DNI_NIF, HoraInicio, HoraFin, Identificacion) " + "VALUES (?, TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS'), TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS'), ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

                ps.setString(1, dni);
                ps.setString(2, inicioCompleto);
                ps.setString(3, finCompleto);
                ps.setInt(4, idCamerino);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Camerino " + idCamerino + " asignado correctamente.");
                return "resultadoArtista";

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
        }
    }

    //mostamos el formulario de contratos
    @GetMapping("/admin/artista/contrato")
    public String mostrarFormularioContrato() {

        return "formularioContrato";

    }

    //guardamos el contrato y la parte de firma
    @PostMapping("/admin/artista/guardarContrato")
    public String guardarContrato(

            @RequestParam int id,
            @RequestParam String dni,
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            @RequestParam double importe,
            @RequestParam String datosBancarios,
            @RequestParam String metodoPago,
            @RequestParam String tipoPago,
            Model model) throws SQLException {

        //fechafin del contrato tiene que ser posterior a fecha inicio
        if (fechaInicio.compareTo(fechaFin) >= 0) {

            model.addAttribute("mensaje", "La fecha de fin debe ser posterior a la de inicio.");
            return "resultadoArtista";

        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            //id de contrato tiene que ser unico
            String sqlCheckId = "SELECT COUNT(*) FROM Contratos WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCheckId)) {

                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {

                    model.addAttribute("mensaje", "El ID de contrato ya existe.");
                    return "resultadoArtista";

                }
            }

            //comprobacion artista
            String sqlCheckArt = "SELECT COUNT(*) FROM Artistas WHERE DNI_NIF = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCheckArt)) {

                ps.setString(1, dni);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) == 0) {

                    model.addAttribute("mensaje", "No existe ningún artista con el DNI proporcionado.");
                    return "resultadoArtista";
                }
            }

            try {
                //insertamos la parte de contratos
                String sqlContrato = "INSERT INTO Contratos (ID, ImporteAcordado, FechaInicio, FechaFin, " + "MetodoDePago, TipoDePago, DatosBancarios) VALUES (?, ?, TO_DATE(?, 'YYYY-MM-DD'), " + "TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?)";

                try (PreparedStatement ps = conn.prepareStatement(sqlContrato)) {

                    ps.setInt(1, id);
                    ps.setDouble(2, importe);
                    ps.setString(3, fechaInicio);
                    ps.setString(4, fechaFin);
                    ps.setString(5, metodoPago);
                    ps.setString(6, tipoPago);
                    ps.setString(7, datosBancarios);
                    ps.executeUpdate();

                }

                //ahora firma
                String sqlFirma = "INSERT INTO Firma (ID, DNI_NIF) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlFirma)) {

                    ps.setInt(1, id);
                    ps.setString(2, dni);
                    ps.executeUpdate();

                }

                conn.commit(); // Confirmamos ambas inserciones
                model.addAttribute("mensaje", "Contrato " + id + " firmado correctamente por el artista " + dni);

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
            return "resultadoArtista";
        }
    }

    //formulario pagos
    @GetMapping("/admin/artista/pago")
    public String mostrarFormularioPagos() {

        return "formularioPagos";

    }



    //post de pagos
    @PostMapping("/admin/artista/registrarPago")
    public String registrarPago(

            @RequestParam int id,
            @RequestParam int numPago,
            Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            //buscamos contrato
            String sqlContrato = "SELECT TIPODEPAGO, DURACIONDELFRACCIONADO FROM Contratos WHERE ID = ?";
            String tipoPago = null;
            int duracionMax = 0;

            try (PreparedStatement ps = conn.prepareStatement(sqlContrato)) {

                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {

                    tipoPago = rs.getString("TIPODEPAGO");
                    duracionMax = rs.getInt("DURACIONDELFRACCIONADO");

                } else {

                    model.addAttribute("mensaje", "El contrato con ID " + id + " no existe.");
                    return "resultadoArtista";
                }
            }

            //nos aseguramos de que el tipo de pago este relleno, poniendo un por defecto
            if (tipoPago == null) {
                tipoPago = "Total";
            }

            //si no es fraccionado, la duracion debe ser 1
            if (!tipoPago.equalsIgnoreCase("Fraccionado") && duracionMax > 1) {
                model.addAttribute("mensaje", "La duración no puede ser mayor de 1 porque el tipo de pago no es fraccionado.");
                return "resultadoArtista";
            }

            //NumPago debe ser menor o igual que duración del fraccionado
            if (numPago > duracionMax) {

                model.addAttribute("mensaje", "El número de pago (" + numPago + ") no puede ser superior a la duración pactada (" + duracionMax + ").");
                return "resultadoArtista";

            }

            //no repetir el numero de pago de un contrato
            String sqlCheckDuplicado = "SELECT COUNT(*) FROM Pagos WHERE ID = ? AND NumPago = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCheckDuplicado)) {

                ps.setInt(1, id);
                ps.setInt(2, numPago);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {

                    model.addAttribute("mensaje", "El pago número " + numPago + " para el contrato " + id + " ya está registrado.");
                    return "resultadoArtista";

                }
            }

            //insertamos el pago
            String sqlInsertPago = "INSERT INTO Pagos (ID, NumPago) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlInsertPago)) {

                ps.setInt(1, id);
                ps.setInt(2, numPago);
                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Pago número " + numPago + " del contrato " + id + " registrado con éxito.");
                return "resultadoArtista";

            } catch (SQLException e) {

                conn.rollback();
                throw e;

            }
        }
    }

    //----FIN-GESTION-ARTISTAS----//
    
    //----COMIENZO-GESTION-CLIENTES----//

     @GetMapping("/cliente/menu")
    public String mostrarMenuCliente() {
        return "menu-cliente";
    }

    @GetMapping("/cliente/menu/alta")
    public String mostrarFormularioAlta() {

        return "alta";
    }

    @GetMapping("/cliente/menu/comprar")
    public String mostrarFormularioCompra() {

        return "comprar";
    }

    @GetMapping("/cliente/menu/modificar")
    public String mostrarFormularioModificar() {

        return "modificar";
    }

    @GetMapping("/cliente/menu/baja")
    public String mostrarFormularioBaja() {

        return "baja";
    }

    @GetMapping("/cliente/menu/encuesta")
    public String mostrarFormularioEncuesta() {

        return "encuesta";
    }

    @GetMapping("/cliente/menu/abono")
    public String mostrarFormularioAbono() {

        return "abono";
    }

    @GetMapping("/cliente/menu/atencion")
    public String mostrarFormularioAtencion() {

        return "atencion";
    }


    @PostMapping("/cliente/menu/alta")
    public String altaCliente(@RequestParam String dni, @RequestParam String correo, @RequestParam String contrasena, @RequestParam String nombre, @RequestParam String apellidos, @RequestParam String telefono,@RequestParam(required = false) String consentimiento,  Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");
            try {
                // Validar consentimiento
                if (consentimiento == null) {
                    model.addAttribute("mensaje", "Debe aceptar el uso de sus datos para continuar.");
                    model.addAttribute("volver", "/Cliente/Menu/alta");
                    return "resultadoCliente";
                }

                // Inserción
                String sql = "INSERT INTO Cliente (CorreoElectronico, Contrasena, Nombre, Apellidos, Telefono, DNI_NIF) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, correo);
                    ps.setString(2, contrasena);
                    ps.setString(3, nombre);
                    ps.setString(4, apellidos);
                    ps.setLong(5, Long.parseLong(telefono));
                    ps.setString(6, dni);
                    ps.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Registro completado con éxito");
                model.addAttribute("volver", "/Cliente/Menu");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Fallo en el registro: " + e.getMessage()); //
            }
        }
    }

    @PostMapping("/cliente/menu/modificar")
    public String modificarCliente(@RequestParam String correo, @RequestParam String pass, @RequestParam String nombre, @RequestParam String apellidos, @RequestParam String telefono, @RequestParam String dni,@RequestParam(required = false) String consentimiento,  Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");
            try {
                // Validar consentimiento
                if (consentimiento == null) {
                    model.addAttribute("mensaje", "Debe aceptar el uso de sus datos para continuar.");
                    model.addAttribute("volver", "/Cliente/Menu/modificar");
                    return "resultadoCliente";
                }
                String sql = "UPDATE Cliente SET CorreoElectronico=?, Contrasena=?, Nombre=?, Apellidos=?, Telefono=? WHERE DNI_NIF=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, correo);
                    ps.setString(2, pass);
                    ps.setString(3, nombre);
                    ps.setString(4, apellidos);
                    ps.setLong(5, Long.parseLong(telefono));
                    ps.setString(6, dni);
                    ps.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Datos modificados correctamente");
                model.addAttribute("volver", "/Cliente/Menu");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Error al modificar: " + e.getMessage());
            }
        }
    }

    @PostMapping("/cliente/menu/baja")
    public String bajaCliente(@RequestParam String dni,@RequestParam(required = false) String consentimiento, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");
            try {
                // Validar consentimiento
                if (consentimiento == null) {
                    model.addAttribute("mensaje", "Debe aceptar el uso de sus datos para continuar.");
                    model.addAttribute("volver", "/Cliente/Menu/modificar");
                    return "resultadoCliente";
                }
                // Validar eventos futuros
                String sqlVerificacion = "SELECT COUNT(*) FROM Corresponde c " + "JOIN Evento e ON c.Nombre = e.Nombre AND c.Localizacion = e.Localizacion AND c.Fecha = e.Fecha " +  "WHERE c.DNI_NIF = ? AND e.Fecha > SYSDATE";
                try (PreparedStatement ps = conn.prepareStatement(sqlVerificacion)) {
                    ps.setString(1, dni);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    if (rs.getInt(1) > 0) throw new SQLException("No puedes darte de baja: tienes entradas futuras");
                }

                // Delete
                String sqlDelete = "DELETE FROM Cliente WHERE DNI_NIF = ?";
                try (PreparedStatement ps = conn.prepareStatement(sqlDelete)) {
                    ps.setString(1, dni);
                    ps.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Cuenta eliminada correctamente");
                model.addAttribute("volver", "/Cliente/login");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Error durante la baja: " + e.getMessage());
            }
        }
    }

    @PostMapping("/cliente/menu/comprar")
    public String comprarEntrada(@RequestParam String nombreEvento, @RequestParam String localizacionEvento, @RequestParam String fechaEvento, @RequestParam int numEntradas, @RequestParam String tipoEntrada, @RequestParam String metodoPago, @RequestParam long numeroTarjeta, @RequestParam String caducidadTarjeta, @RequestParam int codigoVerificacionTarjeta, @RequestParam String dni,  Model model) throws SQLException {


        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Date sqlFechaEvento = Date.valueOf(fechaEvento);
            Date sqlCaducidad = Date.valueOf(caducidadTarjeta);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");

            try {

                String localizador = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

                // Insertar Entrada
                String sqlEntrada = "INSERT INTO Entrada (DNI_NIF, Localizador, NumeroEntradas, TipoEntrada, MetodoPago, NumeroTarjeta) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psEnt = conn.prepareStatement(sqlEntrada)) {
                    psEnt.setString(1, dni);
                    psEnt.setString(2, localizador);
                    psEnt.setInt(3, numEntradas);
                    psEnt.setString(4, tipoEntrada);
                    psEnt.setString(5, metodoPago);
                    psEnt.setLong(6, numeroTarjeta);
                    psEnt.executeUpdate();
                }
                // Insertar Tarjeta
                String sqltarjeta = "INSERT INTO Tarjeta(NumeroTarjeta,FechaCaducidad, CodigoSeguridad) VALUES (?, ?, ?)";
                try (PreparedStatement pstarjeta = conn.prepareStatement(sqltarjeta)) {
                    pstarjeta.setLong(1, numeroTarjeta);
                    pstarjeta.setDate(2, sqlCaducidad);
                    pstarjeta.setInt(3, codigoVerificacionTarjeta);
                    pstarjeta.executeUpdate();
                }

                // Corresponde
                String sqlCorresponde = "INSERT INTO Corresponde (DNI_NIF, Localizador, Nombre, Localizacion, Fecha) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement psCorr = conn.prepareStatement(sqlCorresponde)) {
                    psCorr.setString(1, dni);
                    psCorr.setString(2, localizador);
                    psCorr.setString(3, nombreEvento);
                    psCorr.setString(4, localizacionEvento);
                    psCorr.setDate(5, sqlFechaEvento);
                    psCorr.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Compra realizada. Localizador: " + localizador);
                model.addAttribute("volver", "/Cliente/Menu");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Fallo en la compra: " + e.getMessage());
            }
        }
    }

    @PostMapping("/cliente/menu/encuesta")
    public String enviarEncuesta(@RequestParam String nombreEvento, @RequestParam String locEvento, @RequestParam String fechaEvento, @RequestParam String respuesta, @RequestParam String dni,  Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Date sqlFechaEvento = Date.valueOf(fechaEvento);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");

            try {

                // Validar Encuesta duplicada
                String sqlDup = "SELECT COUNT(*) FROM Encuesta WHERE DNI_NIF=? AND Nombre=? AND Localizacion=? AND Fecha=?";
                try (PreparedStatement ps = conn.prepareStatement(sqlDup)) {
                    ps.setString(1, dni);
                    ps.setString(2, nombreEvento);
                    ps.setString(3, locEvento);
                    ps.setDate(4, sqlFechaEvento);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    if (rs.getInt(1) > 0) throw new SQLException("Ya has rellenado esta encuesta");
                }

                // Insertar
                String sqlInsert = "INSERT INTO Encuesta (DNI_NIF, RespuestaEncuesta, Nombre, Localizacion, Fecha) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setString(1, dni);
                    ps.setString(2, respuesta);
                    ps.setString(3, nombreEvento);
                    ps.setString(4, locEvento);
                    ps.setDate(5, sqlFechaEvento);
                    ps.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Encuesta enviada correctamente");
                model.addAttribute("volver", "/Cliente/Menu");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Error encuesta: " + e.getMessage());
            }
        }
    }

    @PostMapping("/cliente/menu/abono")
    public String solicitarAbono(@RequestParam String tipoAbono, @RequestParam String comprobacionAdmin, @RequestParam String dni, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");

            try {
                if ("INVALIDO".equals(comprobacionAdmin)){
                    throw new SQLException("Requisitos no cumplidos");
                }

                String sql = "INSERT INTO Tiene (Tipo, Comprobacion, DNI_NIF) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, tipoAbono);
                    ps.setString(2, comprobacionAdmin);
                    ps.setString(3, dni);
                    ps.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Abono aplicado correctamente");
                model.addAttribute("volver", "/Cliente/Menu");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Error abono: " + e.getMessage());
            }
        }
    }

    @PostMapping("/cliente/menu/atencion")
    public String atencionCliente(@RequestParam String tipoSolicitud, @RequestParam String comprobacionAdmin, @RequestParam String detalleSolicitud,@RequestParam String dni, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            java.sql.Savepoint savepoint = conn.setSavepoint("PuntoSeguro");
            try {
                if ("INVALIDO".equals(comprobacionAdmin)){
                    throw new SQLException("Requisitos no cumplidos");
                }
                String sql = "INSERT INTO Enviar (Tipo, Comprobacion,detalleSolicitud, DNI_NIF) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, tipoSolicitud);
                    ps.setString(2, comprobacionAdmin);
                    ps.setString(3, detalleSolicitud);
                    ps.setString(4, dni);
                    ps.executeUpdate();
                }

                conn.commit();
                model.addAttribute("mensaje", "Solicitud enviada correctamente");
                model.addAttribute("volver", "/Cliente/Menu");
                return "resultadoCliente";

            } catch (Exception e) {
                conn.rollback(savepoint);
                throw new SQLException("Error atención al cliente: " + e.getMessage());
            }
        }
    }



    //----FIN-GESTION-CLIENTES----//

    //----INICIO-GESTION-INFRAESTRUCTURA----//

    @GetMapping("/indiceAdri-html")
    public String indiceHTML(Model model) throws SQLException {
        return "infraestructura-opciones.html";
    }

    @GetMapping("/infraestructuraaveria")
    public String infraestructuraaveria(Model model) throws SQLException {
        return "infraestructuraaveria";
    }
    @GetMapping("/infraestructurafestival")
    public String infraestructurafestival(Model model) throws SQLException {
        return "infraestructurafestival";
    }

    @PostMapping("/actualizarIDFestival")
    public String actualizarTexto(@RequestParam("TextoIDFestival") String texto, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            String sqlMapa = "SELECT * FROM MAPA WHERE Id = ?";
            String sqlPermisos = "SELECT * FROM PERMISOS WHERE IdPermiso = ?";

            try (PreparedStatement psMapa = conn.prepareStatement(sqlMapa);
                 PreparedStatement psPermisos = conn.prepareStatement(sqlPermisos)) {
                psMapa.setString(1, texto);
                psPermisos.setString(1, texto);

                ResultSet rsMapa = psMapa.executeQuery();
                ResultSet rsPermisos = psPermisos.executeQuery();

                if (rsMapa.next() && rsPermisos.next()) {

                    String nombreMapa = rsMapa.getString(2);
                    String detallesMapa = rsMapa.getString(3);
                    String posicionMapa = rsMapa.getString(4);

                    String descripcionPermisos = rsPermisos.getString(2);
                    String TipoPermisos = rsPermisos.getString(3);

                    conn.commit();

                    //En lugar de devolver el texto directamente, lo guardamos en el 'modelo'
                    //"mensaje" será la variable que usaremos luego en el HTML
                    model.addAttribute("nombreMapa", nombreMapa);
                    model.addAttribute("detallesMapa", detallesMapa);
                    model.addAttribute("posicionMapa", posicionMapa);

                    model.addAttribute("descripcionPermisos", descripcionPermisos);
                    model.addAttribute("tipoPermisos", TipoPermisos);

                    //Devolvemos el NOMBRE del archivo HTML (sin .html)
                    return "infraestructurafestival";
                }
            }

            //Si falla o no encuentra nada
            model.addAttribute("mensaje", "No se encontraron datos");
            return "resultado"; //Devolvemos la misma plantilla
        }
    }


    @PostMapping("/actualizarIDPersonal")
    public String actualizarIDPersonal (@RequestParam("TextoIDPersonal") String texto, Model model) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Asegúrate de manejar la transacción
            String sqlPersonal = "SELECT * FROM PERSONALDELFESTIVAL WHERE IdPersonal = ?";

            try (PreparedStatement psPersonal = conn.prepareStatement(sqlPersonal)) {
                psPersonal.setString(1, texto);
                try (ResultSet rsPersonal = psPersonal.executeQuery()) {
                    if (rsPersonal.next()) {
                        // Extraer datos de la BD
                        String nombrePersonal = rsPersonal.getString(2);
                        String correoPersonal = rsPersonal.getString(3);

                        conn.commit(); // Confirmar cambios

                        // Agregar datos al modelo
                        model.addAttribute("nombrePersonal", nombrePersonal);
                        model.addAttribute("correoPersonal", correoPersonal);

                        return "infraestructuraaveria"; // Enviar a la página index.html
                    }
                }
            }
        }


        // Si no encuentra ningún dato
        model.addAttribute("mensaje", "No se encontraron datos");
        return "resultado"; // Devuelve la página resultado.html
    }

    @PostMapping("/insertarAveria")
    public String insertarAveria (@RequestParam("TextoAveria") String texto, Model model) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Asegúrate de manejar la transacción
            String sqlInsert = "INSERT INTO AVERIAS VALUES (?, ?)";
            String sqlGetCount = "SELECT COUNT(*) FROM AVERIAS";

            try (PreparedStatement psInsert = conn.prepareStatement(sqlInsert);
                 PreparedStatement psGetCount = conn.prepareStatement(sqlGetCount);) {

                int count = -1;
                try (ResultSet rsGetCount = psGetCount.executeQuery()) {
                    if (rsGetCount.next()) {
                        count = rsGetCount.getInt(1);
                    }
                }

                psInsert.setString(1,String.valueOf(count+1) );
                psInsert.setString(2, texto);

                try (ResultSet rsInsert = psInsert.executeQuery()){

                    conn.commit(); // Confirmar cambios

                    return "infraestructuraaveria"; // Enviar a la página index.html
                }

            }
        }

    }
    
    //----FIN-GESTION-INFRAESTRUCTURA----//


    // ======== = RF3 - GESTIÓN DE PATROCINADORES ==========

    @GetMapping("/admin/patrocinadores/opciones")
    public String mostrarOpcionesPatrocinadores() {
        return "patrocinadores-opciones";
    }

    // ========== RF3.1 - REGISTRO DE PATROCINADORES ==========

    @GetMapping("/admin/patrocinadores/nuevo")
    public String mostrarFormularioPatrocinador() {
        return "formulario-patrocinador";
    }

    @PostMapping("/admin/patrocinadores/nuevo")
    @ResponseBody
    public String registrarPatrocinador(
            @RequestParam String dni,
            @RequestParam String nombrePersonaContacto,
            @RequestParam String correoElectronicoPersonaContacto,
            @RequestParam String numeroTelefono,
            @RequestParam String nombreEmpresa,
            @RequestParam(required = false) String tipoBeneficios,
            @RequestParam(required = false) String descripcionBeneficios
    ) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Insertar persona de contacto
                String sqlPersona = "INSERT INTO PERSONA_CONTACTO (DNI, NombrePersonaContacto, CorreoElectronicoPersonaContacto, NumeroTelefono) VALUES (?, ?, ?, ?)";
                try (PreparedStatement psPersona = conn.prepareStatement(sqlPersona)) {
                    psPersona.setString(1, dni);
                    psPersona.setString(2, nombrePersonaContacto);
                    psPersona.setString(3, correoElectronicoPersonaContacto);
                    psPersona.setString(4, numeroTelefono);
                    psPersona.executeUpdate();
                }

                // 2. Insertar patrocinador
                String sqlPatrocinador = "INSERT INTO PATROCINADORES (NombreEmpresa, DNI, TipoBeneficios, DescripcionBeneficios) VALUES (?, ?, ?, ?)";
                try (PreparedStatement psPatrocinador = conn.prepareStatement(sqlPatrocinador)) {
                    psPatrocinador.setString(1, nombreEmpresa);
                    psPatrocinador.setString(2, dni);
                    psPatrocinador.setString(3, tipoBeneficios);
                    psPatrocinador.setString(4, descripcionBeneficios);
                    psPatrocinador.executeUpdate();
                }

                conn.commit();
                return "Patrocinador registrado correctamente";

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ========== RF3.2 - CONSULTA DE PATROCINADORES ==========

    @GetMapping("/admin/patrocinadores/lista")
    public String listarPatrocinadores(Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "SELECT p.NombreEmpresa, pc.CorreoElectronicoPersonaContacto " +
                     "FROM PATROCINADORES p " +
                    "JOIN PERSONA_CONTACTO pc ON p.DNI = pc.DNI " +
                    "ORDER BY p.NombreEmpresa";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();

                java.util.List<java.util.Map<String, String>> patrocinadores = new java.util.ArrayList<>();

                while (rs.next()) {
                    java.util.Map<String, String> patrocinador = new java.util.HashMap<>();
                    patrocinador.put("nombreEmpresa", rs.getString("NombreEmpresa"));
                    patrocinador.put("correoElectronicoPersonaContacto", rs.getString("CorreoElectronicoPersonaContacto"));
                    patrocinadores.add(patrocinador);
                }

                model.addAttribute("patrocinadores", patrocinadores);
                conn.commit();
                return "lista-patrocinadores";
            }
        }
    }

    // ========== RF3.3 - REGISTRO DE CONTRATOS ==========

    @GetMapping("/admin/contratos/nuevo")
    public String mostrarFormularioContrato() {
        return "formulario-contrato";
    }

    @PostMapping("/admin/contratos/nuevo")
    @ResponseBody
    public String registrarContrato(
            @RequestParam int ID,
            @RequestParam String nombreEmpresa,
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            @RequestParam double importeAcordado,
            @RequestParam String datosBancarios,
            @RequestParam String metodoDePago,
            @RequestParam String tipoDePago,
            @RequestParam(required = false) Integer duracionDelFraccionado
    ) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            java.sql.Savepoint savepoint = conn.setSavepoint("AntesContrato");

            try {
                // 1. Insertar contrato con ID manual
                String sqlContrato = "INSERT INTO CONTRATOS (ID, ImporteAcordado, FechaInicio, FechaFin, DatosBancarios, MetodoDePago, TipoDePago, DuracionDelFraccionado) " +
                        "VALUES (?, ?, TO_DATE(?, 'YYYY-MM-DD'), TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, ?)";

                try (PreparedStatement psContrato = conn.prepareStatement(sqlContrato)) {
                    psContrato.setInt(1, ID);
                    psContrato.setDouble(2, importeAcordado);
                    psContrato.setString(3, fechaInicio);
                    psContrato.setString(4, fechaFin);
                    psContrato.setString(5, datosBancarios);
                    psContrato.setString(6, metodoDePago);
                    psContrato.setString(7, tipoDePago);

                    if (duracionDelFraccionado != null) {
                        psContrato.setInt(8, duracionDelFraccionado);
                    } else {
                        psContrato.setNull(8, java.sql.Types.INTEGER);
                    }

                    psContrato.executeUpdate();
                }

                // 2. Insertar firma
                String sqlFirma = "INSERT INTO FIRMA_P(ID, NombreEmpresa) VALUES (?, ?)";
                try (PreparedStatement psFirma = conn.prepareStatement(sqlFirma)) {
                    psFirma.setInt(1, ID);
                    psFirma.setString(2, nombreEmpresa);
                    psFirma.executeUpdate();
                }

                conn.commit();
                return "Contrato registrado correctamente con ID: " + ID;

            } catch (SQLException e) {
                conn.rollback(savepoint);
                throw e;
            }
        }
    }

    // ========== RF3.4 - CONTROL DE BENEFICIOS ==========

    @GetMapping("/admin/beneficios/actualizar")
    public String mostrarFormularioActualizarBeneficios() {
        return "formulario-actualizar-beneficios";
    }

    @PostMapping("/admin/beneficios/actualizar")
    @ResponseBody
    public String actualizarBeneficios(
            @RequestParam String nombreEmpresa,
            @RequestParam String tipoBeneficios,
            @RequestParam String descripcionBeneficios
    ) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String sql = "UPDATE PATROCINADORES SET TipoBeneficios = ?, DescripcionBeneficios = ? WHERE NombreEmpresa = ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, tipoBeneficios);
                    ps.setString(2, descripcionBeneficios);
                    ps.setString(3, nombreEmpresa);

                    int filasAfectadas = ps.executeUpdate();

                    if (filasAfectadas > 0) {
                        conn.commit();
                        return "Beneficios actualizados correctamente";
                    } else {
                        conn.rollback();
                        return "No se encontró el patrocinador";
                    }
                }

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ========== RF3.5 - GESTIÓN DE PAGOS ==========

    @GetMapping("/admin/pagos/nuevo")
    public String mostrarFormularioPago() {
        return "formulario-pago";
    }

    @PostMapping("/admin/pagos/nuevo")
    @ResponseBody
    public String registrarPago(
            @RequestParam int ID,
            @RequestParam int numPago
    ) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String sql = "INSERT INTO PAGOS(ID, NumPago) VALUES (?, ?)";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, ID);
                    ps.setInt(2, numPago);
                    ps.executeUpdate();
                }

                conn.commit();
                return "Pago registrado correctamente";

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }




    @GetMapping("/")
    public String mostrarMenuPrincipal() {
            
        return "menuprincipal"; 
            
    }

    
    @PostMapping("/admin/menu/navegar")
    public String navegarMenu(@RequestParam String destino) {
            
        switch (destino) {
                        
            case "clientes": return "redirect:/cliente/menu";
            case "artistas": return "redirect:/admin/artista/menu";
            case "patrocinadores": return "redirect:/admin/patrocinadores/opciones";
            case "servicios": return "redirect:/admin/servicios";
            case "infraestructuras": return "redirect:/indiceAdri-html";
            default: return "redirect:/admin/menu-principal";
                        
        }
    }
}

        


