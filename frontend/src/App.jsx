import './App.css'
import JobSearch from './JobSearch'

function App() {
  return (
    <div className="app-shell">
      <header>
        <a className="brand" href="/" aria-label="AllEZ 홈">
          AllEZ
        </a>
        <span>채용공고 통합보기</span>
      </header>
      <main>
        <JobSearch />
      </main>
      <footer><b>AllEZ</b><span>잡코리아와 사람인 공고를 한곳에서</span></footer>
    </div>
  )
}

export default App
