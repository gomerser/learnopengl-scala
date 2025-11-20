package learnopengl_2_3

import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

val vertexShaderSource =
  """#version 330 core
      |layout (location = 0) in vec3 aPos;
      |void main()
      |{
      |   gl_Position = vec4(aPos.x, aPos.y, aPos.z, 1.0);
      |}
      |""".stripMargin
val fragmentShaderSource =
  """#version 330 core
      |out vec4 FragColor;
      |void main()
      |{
      |   FragColor = vec4(1.0f, 0.5f, 0.2f, 1.0f);
      |}
      |""".stripMargin

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")

  // enable error callback
  GLFWErrorCallback.createPrint(System.err).set()

  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

  if (System.getProperty("os.name").toLowerCase.contains("mac"))
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

  // glfw window creation
  // --------------------
  val window = glfwCreateWindow(SCR_WIDTH, SCR_HEIGHT, "LearnOpenGL", 0, 0)
  if (window == 0L) {
    glfwTerminate()
    throw new RuntimeException("Failed to create GLFW window")
  }
  glfwMakeContextCurrent(window)
  glfwSetFramebufferSizeCallback(window, framebuffer_size_callback)

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // build and compile our shader program
  // ------------------------------------
  // vertex shader
  val vertexShader = glCreateShader(GL_VERTEX_SHADER)
  glShaderSource(vertexShader, vertexShaderSource)
  glCompileShader(vertexShader)
  // check for shader compile errors
  if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
    val log = glGetShaderInfoLog(vertexShader)
    throw new RuntimeException(
      "ERROR::SHADER::VERTEX::COMPILATION_FAILED:\n" + log
    )
  }
  // fragment shader
  val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
  glShaderSource(fragmentShader, fragmentShaderSource)
  glCompileShader(fragmentShader)
  // check for shader compile errors
  if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
    val log = glGetShaderInfoLog(fragmentShader)
    throw new RuntimeException(
      "ERROR::SHADER::FRAGMENT::COMPILATION_FAILED:\n" + log
    )
  }
  // link shaders
  val shaderProgram = glCreateProgram()
  glAttachShader(shaderProgram, vertexShader)
  glAttachShader(shaderProgram, fragmentShader)
  glLinkProgram(shaderProgram)
  // check for linking errors
  if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
    val log = glGetShaderInfoLog(shaderProgram)
    throw new RuntimeException(
      "ERROR::SHADER::PROGRAM::LINKING_FAILED:\n" + log
    )
  }
  glDeleteShader(vertexShader)
  glDeleteShader(fragmentShader)

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  // add a new set of vertices to form a second triangle (a total of 6 vertices) the vertex attribute configuration remains the same (still one 3-float position vector per vertex)
  val vertices: Array[Float] = Array(
    // first triangle
    -0.9f, -0.5f, 0.0f, // left
    -0.0f, -0.5f, 0.0f, // right
    -0.45f, 0.5f, 0.0f, // top
    // second triangle
    0.0f, -0.5f, 0.0f, // left
    0.9f, -0.5f, 0.0f, // right
    0.45f, 0.5f, 0.0f // top
  )

  val VAO = glGenVertexArrays()
  val VBO = glGenBuffers()

  // bind the Vertex Array Object first, then bind and set vertex buffer(s), and then configure vertex attributes(s).
  glBindVertexArray(VAO)

  glBindBuffer(GL_ARRAY_BUFFER, VBO)
  val vertexBuf = MemoryUtil.memAllocFloat(vertices.length)
  vertexBuf.put(vertices).flip()
  glBufferData(GL_ARRAY_BUFFER, vertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(vertexBuf)

  glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(0)

  // note that this is allowed, the call to glVertexAttribPointer registered VBO as the vertex attribute's bound vertex buffer object so afterwards we can safely unbind
  glBindBuffer(GL_ARRAY_BUFFER, 0)

  // You can unbind the VAO afterwards so other VAO calls won't accidentally modify this VAO, but this rarely happens. Modifying other
  // VAOs requires a call to glBindVertexArray anyways so we generally don't unbind VAOs (nor VBOs) when it's not directly necessary.
  glBindVertexArray(0)

  // uncomment this call to draw in wireframe polygons.
  // glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {
    // input
    // -----
    processInput(window)

    // render
    // ------
    glClearColor(0.2f, 0.3f, 0.3f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT)

    // draw our first triangle
    glUseProgram(shaderProgram)
    glBindVertexArray(
      VAO
    ) // seeing as we only have a single VAO there's no need to bind it every time, but we'll do so to keep things a bit more organized
    glDrawArrays(
      GL_TRIANGLES,
      0,
      6
    ) // set the count to 6 since we're drawing 6 vertices now (2 triangles) not 3!
    // glBindVertexArray(0) // no need to unbind it every time

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(VAO)
  glDeleteBuffers(VBO)
  glDeleteProgram(shaderProgram)

  // glfw: terminate, clearing all previously allocated GLFW resources.
  // ------------------------------------------------------------------
  glfwTerminate()

// process all input: query GLFW whether relevant keys are pressed/released this frame and react accordingly
// ---------------------------------------------------------------------------------------------------------
def processInput(window: Long): Unit = {
  if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
    glfwSetWindowShouldClose(window, true)
}

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}
