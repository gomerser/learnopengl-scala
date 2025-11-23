package learnopengl_2_5

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
val fragmentShader1Source =
  """#version 330 core
      |out vec4 FragColor;
      |void main()
      |{
      |   FragColor = vec4(1.0f, 0.5f, 0.2f, 1.0f);
      |}
      |""".stripMargin
val fragmentShader2Source =
  """#version 330 core
      |out vec4 FragColor;
      |void main()
      |{
      |   FragColor = vec4(1.0f, 01.0f, 0.0f, 1.0f);
      |}
      |""".stripMargin

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
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

  // tell GLFW to capture our mouse
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
  
  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // build and compile our shader program
  // ------------------------------------
  // we skipped compile log checks this time for readability (if you do encounter issues, add the compile-checks! see previous code samples)
  val vertexShader = glCreateShader(GL_VERTEX_SHADER)
  val fragmentShaderOrange = glCreateShader(
    GL_FRAGMENT_SHADER
  ) // the first fragment shader that outputs the color orange
  val fragmentShaderYellow = glCreateShader(
    GL_FRAGMENT_SHADER
  ) // the second fragment shader that outputs the color yellow
  val shaderProgramOrange = glCreateProgram()
  val shaderProgramYellow = glCreateProgram() // the second shader program
  glShaderSource(vertexShader, vertexShaderSource)
  glCompileShader(vertexShader)
  glShaderSource(fragmentShaderOrange, fragmentShader1Source)
  glCompileShader(fragmentShaderOrange)
  glShaderSource(fragmentShaderYellow, fragmentShader2Source)
  glCompileShader(fragmentShaderYellow)
  // link the first program object
  glAttachShader(shaderProgramOrange, vertexShader)
  glAttachShader(shaderProgramOrange, fragmentShaderOrange)
  glLinkProgram(shaderProgramOrange)
  // then link the second program object using a different fragment shader (but same vertex shader)
  // this is perfectly allowed since the inputs and outputs of both the vertex and fragment shaders are equally matched.
  glAttachShader(shaderProgramYellow, vertexShader)
  glAttachShader(shaderProgramYellow, fragmentShaderYellow)
  glLinkProgram(shaderProgramYellow)

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val firstTriangle: Array[Float] = Array(
    -0.9f, -0.5f, 0.0f, // left
    -0.0f, -0.5f, 0.0f, // right
    -0.45f, 0.5f, 0.0f // top
  )
  val secondTriangle: Array[Float] = Array(
    0.0f, -0.5f, 0.0f, // left
    0.9f, -0.5f, 0.0f, // right
    0.45f, 0.5f, 0.0f // top
  )
  // Allocate arrays for VAOs and VBOs
  val VAOs = Array.fill(2)(0)
  val VBOs = Array.fill(2)(0)

  // LWJGL does NOT support glGen* with arrays directly.
  // You must use MemoryStack or generate one-by-one.
  // Here we generate one-by-one (simple and clear):

  VAOs(0) = glGenVertexArrays()
  VAOs(1) = glGenVertexArrays()

  VBOs(0) = glGenBuffers()
  VBOs(1) = glGenBuffers()

  // first triangle setup
  // --------------------
  glBindVertexArray(VAOs(0))
  glBindBuffer(GL_ARRAY_BUFFER, VBOs(0))
  val buf1 = MemoryUtil.memAllocFloat(firstTriangle.length)
  buf1.put(firstTriangle).flip()
  glBufferData(GL_ARRAY_BUFFER, buf1, GL_STATIC_DRAW)
  MemoryUtil.memFree(buf1)
  glVertexAttribPointer(
    0,
    3,
    GL_FLOAT,
    false,
    3 * java.lang.Float.BYTES,
    0L
  ) // Vertex attributes stay the same
  glEnableVertexAttribArray(0)
  // glBindVertexArray(0) // no need to unbind at all as we directly bind a different VAO the next few lines

  // second triangle setup
  // ---------------------
  glBindVertexArray(VAOs(1)) // note that we bind to a different VAO now
  glBindBuffer(GL_ARRAY_BUFFER, VBOs(1)) // and a different VBO
  val buf2 = MemoryUtil.memAllocFloat(secondTriangle.length)
  buf2.put(secondTriangle).flip()
  glBufferData(GL_ARRAY_BUFFER, buf2, GL_STATIC_DRAW)
  MemoryUtil.memFree(buf2)
  glVertexAttribPointer(
    0,
    3,
    GL_FLOAT,
    false,
    0,
    0L
  ) // because the vertex data is tightly packed we can also specify 0 as the vertex attribute's stride to let OpenGL figure it out
  glEnableVertexAttribArray(0)
  // glBindVertexArray(0) // not really necessary as well, but beware of calls that could affect VAOs while this one is bound (like binding element buffer objects, or enabling/disabling vertex attributes)

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

    // now when we draw the triangle we first use the vertex and orange fragment shader from the first program
    glUseProgram(shaderProgramOrange)
    // draw the first triangle using the data from our first VAO
    glBindVertexArray(VAOs(0))
    glDrawArrays(
      GL_TRIANGLES,
      0,
      3
    ) // this call should output an orange triangle
    // then we draw the second triangle using the data from the second VAO
    // when we draw the second triangle we want to use a different shader program so we switch to the shader program with our yellow fragment shader.
    glUseProgram(shaderProgramYellow)
    glBindVertexArray(VAOs(1))
    glDrawArrays(
      GL_TRIANGLES,
      0,
      3
    ) // this call should output a yellow triangle

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(VAOs)
  glDeleteBuffers(VBOs)
  glDeleteProgram(shaderProgramOrange)
  glDeleteProgram(shaderProgramYellow)

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
